package com.syntia.ai.service;

import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.model.dto.FiltrosBdns;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio de integración con la API pública de la BDNS
 * (Base de Datos Nacional de Subvenciones).
 * <p>
 * Endpoint real descubierto de la SPA Angular del portal:
 * GET https://www.infosubvenciones.es/bdnstrans/api/convocatorias/busqueda?vpn=GE&vln=es&numPag={pagina}&tamPag={tamano}
 * <p>
 * La API devuelve un JSON paginado con el campo {@code content} (lista de convocatorias)
 * y {@code totalElements} (total de registros en BDNS, ~615.000).
 * <p>
 * El certificado SSL del servidor gubernamental no está en el truststore por defecto de Java,
 * por lo que se configura un SSLContext permisivo para estas peticiones.
 */
@Slf4j
@Service
public class BdnsClientService {

    private static final String BDNS_BUSQUEDA =
            "https://www.infosubvenciones.es/bdnstrans/api/convocatorias/busqueda";

    /** TTL del caché de detalles BDNS: 1 hora. Reduce latencia en guías enriquecidas. */
    private static final long TTL_DETALLE_MS = 3_600_000L;

    private record CachedDetalle(String texto, Instant savedAt) {}
    private final ConcurrentHashMap<String, CachedDetalle> cacheDetalle = new ConcurrentHashMap<>();

    @Value("${bdns.client.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${bdns.client.read-timeout-ms:30000}")
    private int readTimeoutMs;

    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
                .requestFactory(createSslPermissiveFactory(connectTimeoutMs, readTimeoutMs))
                .defaultHeader("Accept", "application/json")
                .build();
        log.info("BdnsClientService iniciado — connectTimeout={}ms readTimeout={}ms",
                connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Importa convocatorias reales desde la API pública de la BDNS.
     *
     * @param pagina número de página (0-indexed)
     * @param tamano registros por página (máximo 50 recomendado por la API)
     * @return lista de ConvocatoriaDTO mapeados desde la respuesta de BDNS
     * @throws BdnsException si la API no está disponible o devuelve error
     */
    @Retryable(
        retryFor = {ResourceAccessException.class, RestClientException.class},
        noRetryFor = HttpClientErrorException.class,
        maxAttemptsExpression = "${bdns.client.max-reintentos:3}",
        backoff = @Backoff(delayExpression = "${bdns.client.reintento-delay-ms:1500}", multiplier = 2.0)
    )
    public List<ConvocatoriaDTO> importar(int pagina, int tamano) {
        log.info("Consultando API BDNS real: pagina={} tamano={}", pagina, tamano);

        @SuppressWarnings("unchecked")
        Map<String, Object> respuesta = restClient.get()
                .uri(BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag={pag}&tamPag={tam}",
                        pagina, Math.min(tamano, 50))
                .retrieve()
                .body(Map.class);

        if (respuesta == null) {
            throw new BdnsException("La API de BDNS devolvió una respuesta vacía");
        }

        Object totalObj = respuesta.get("totalElements");
        log.info("BDNS: totalElements={}", totalObj);

        return mapearRespuesta(respuesta);
    }

    /**
     * Busca convocatorias en toda la BDNS (615.000+) filtrando por palabras clave.
     * Devuelve hasta {@code tamano} resultados relevantes por página.
     * <p>
     * Parámetro API: {@code descripcion} = texto de búsqueda,
     * {@code descripcionTipoBusqueda} = 1 (contiene todas las palabras).
     *
     * @param keywords palabras clave de búsqueda (p.ej. "digitalizacion pyme tecnologia")
     * @param pagina   número de página (0-indexed)
     * @param tamano   registros por página (máximo 50)
     * @return lista de ConvocatoriaDTO que coinciden con la búsqueda
     */
    /**
     * Importa convocatorias filtrando por eje territorial (nivel1 y opcionalmente nivel2).
     * No aplica filtro de vigencia — recupera todas las convocatorias del eje.
     * Usado por la importación masiva por estrategia territorial.
     *
     * @param nivel1 ámbito: ESTADO, AUTONOMICA, LOCAL, OTROS
     * @param nivel2 comunidad autónoma (solo para AUTONOMICA, null en el resto)
     * @param pagina número de página (0-indexed)
     * @param tamano registros por página (máximo 50)
     */
    /** Resultado paginado de un eje BDNS: registros mapeados + total de elementos del eje. */
    public record PaginaBdns(List<ConvocatoriaDTO> items, long totalElements) {}

    @Retryable(
        retryFor = {ResourceAccessException.class, RestClientException.class},
        noRetryFor = HttpClientErrorException.class,
        maxAttemptsExpression = "${bdns.client.max-reintentos:3}",
        backoff = @Backoff(delayExpression = "${bdns.client.reintento-delay-ms:1500}", multiplier = 2.0)
    )
    public PaginaBdns importarPorEje(String nivel1, String nivel2, int pagina, int tamano) {
        StringBuilder url = new StringBuilder(BDNS_BUSQUEDA)
                .append("?vpn=GE&vln=es&numPag=").append(pagina)
                .append("&tamPag=").append(Math.min(tamano, 50))
                .append("&nivel1=").append(nivel1);

        if (nivel2 != null && !nivel2.isBlank()) {
            url.append("&nivel2=").append(URLEncoder.encode(nivel2, StandardCharsets.UTF_8));
        }

        log.debug("BDNS importarPorEje: nivel1={} nivel2={} pag={}", nivel1, nivel2, pagina);

        @SuppressWarnings("unchecked")
        Map<String, Object> respuesta = restClient.get()
                .uri(url.toString())
                .retrieve()
                .body(Map.class);

        if (respuesta == null) return new PaginaBdns(List.of(), 0L);

        long total = 0L;
        Object totalObj = respuesta.get("totalElements");
        if (totalObj instanceof Number n) total = n.longValue();

        return new PaginaBdns(mapearRespuesta(respuesta), total);
    }

    @Retryable(
        retryFor = {ResourceAccessException.class, RestClientException.class},
        noRetryFor = HttpClientErrorException.class,
        maxAttemptsExpression = "${bdns.client.max-reintentos:3}",
        backoff = @Backoff(delayExpression = "${bdns.client.reintento-delay-ms:1500}", multiplier = 2.0)
    )
    public List<ConvocatoriaDTO> buscarPorTexto(String keywords, int pagina, int tamano) {
        log.info("BDNS búsqueda por texto: '{}' pagina={} tamano={}", keywords, pagina, tamano);

        // vigente=true → solo convocatorias con plazo abierto
        @SuppressWarnings("unchecked")
        Map<String, Object> respuesta = restClient.get()
                .uri(BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag={pag}&tamPag={tam}" +
                                "&descripcion={desc}&descripcionTipoBusqueda=1&vigente=true",
                        pagina, Math.min(tamano, 50), keywords)
                .retrieve()
                .body(Map.class);

        if (respuesta == null) {
            throw new BdnsException("BDNS devolvió respuesta vacía para búsqueda: " + keywords);
        }

        Object totalObj = respuesta.get("totalElements");
        log.info("BDNS búsqueda '{}': totalElements={}", keywords, totalObj);

        return mapearRespuesta(respuesta);
    }

    // @inferido — nivel1/nivel2 son parámetros observados en el portal Angular de BDNS, no documentados oficialmente
    public List<ConvocatoriaDTO> buscarPorTextoFiltrado(String keyword, String ccaa) {
        if (ccaa == null) {
            return buscarPorTexto(keyword, 0, 15);
        }

        log.info("BDNS búsqueda filtrada: keyword='{}' ccaa='{}'", keyword, ccaa);

        List<ConvocatoriaDTO> combinadas = new CopyOnWriteArrayList<>();
        String ccaaEncoded = URLEncoder.encode(ccaa, StandardCharsets.UTF_8);

        @SuppressWarnings("resource")
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            // Llamada A: convocatorias estatales (siempre relevantes independientemente de la CCAA)
            CompletableFuture<Void> futuroEstatal = CompletableFuture.runAsync(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respuesta = restClient.get()
                            .uri(BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag=0&tamPag=10" +
                                            "&descripcion={desc}&descripcionTipoBusqueda=1&vigente=true" +
                                            "&nivel1=ESTADO",
                                    keyword)
                            .retrieve()
                            .body(Map.class);
                    if (respuesta != null) {
                        combinadas.addAll(mapearRespuesta(respuesta));
                    }
                    log.debug("BDNS filtrada ESTADO '{}': {} resultados", keyword, respuesta != null ? respuesta.get("totalElements") : 0);
                } catch (Exception e) {
                    log.warn("Error en búsqueda BDNS ESTADO keyword='{}': {}", keyword, e.getMessage());
                }
            }, executor);

            // Llamada B: convocatorias autonómicas de la CCAA del usuario
            CompletableFuture<Void> futuroAutonomica = CompletableFuture.runAsync(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respuesta = restClient.get()
                            .uri(BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag=0&tamPag=10" +
                                            "&descripcion={desc}&descripcionTipoBusqueda=1&vigente=true" +
                                            "&nivel1=AUTONOMICA&nivel2=" + ccaaEncoded,
                                    keyword)
                            .retrieve()
                            .body(Map.class);
                    if (respuesta != null) {
                        combinadas.addAll(mapearRespuesta(respuesta));
                    }
                    log.debug("BDNS filtrada AUTONOMICA '{}' ccaa='{}': {} resultados",
                            keyword, ccaa, respuesta != null ? respuesta.get("totalElements") : 0);
                } catch (Exception e) {
                    log.warn("Error en búsqueda BDNS AUTONOMICA keyword='{}' ccaa='{}': {}", keyword, ccaa, e.getMessage());
                }
            }, executor);

            CompletableFuture.allOf(futuroEstatal, futuroAutonomica).join();
        } finally {
            executor.shutdown();
        }

        // Deduplicar por idBdns
        Set<String> idsBdnsVistos = new HashSet<>();
        List<ConvocatoriaDTO> resultado = new ArrayList<>();
        for (ConvocatoriaDTO dto : combinadas) {
            if (dto.getIdBdns() != null && !dto.getIdBdns().isBlank()) {
                if (idsBdnsVistos.contains(dto.getIdBdns())) continue;
                idsBdnsVistos.add(dto.getIdBdns());
            }
            resultado.add(dto);
        }

        log.info("BDNS filtrada '{}' ccaa='{}': {} combinadas, {} tras dedup", keyword, ccaa, combinadas.size(), resultado.size());
        return resultado;
    }

    // ── Recover (agotados los reintentos) ────────────────────────────────────

    @Recover
    public List<ConvocatoriaDTO> recoverImportar(RestClientException ex, int pagina, int tamano) {
        log.error("BDNS importar: agotados reintentos (pagina={} tamano={}): {}", pagina, tamano, ex.getMessage());
        throw new BdnsException("BDNS no disponible tras " + "${bdns.client.max-reintentos:3}" + " intentos", ex);
    }

    @Recover
    public PaginaBdns recoverImportarPorEje(RestClientException ex,
            String nivel1, String nivel2, int pagina, int tamano) {
        log.error("BDNS importarPorEje: agotados reintentos (nivel1={} nivel2={} pag={}): {}",
                nivel1, nivel2, pagina, ex.getMessage());
        throw new BdnsException("BDNS no disponible para eje " + nivel1 + " tras reintentos", ex);
    }

    @Recover
    public List<ConvocatoriaDTO> recoverBuscarPorTexto(RestClientException ex,
            String keywords, int pagina, int tamano) {
        log.error("BDNS buscarPorTexto: agotados reintentos (keywords='{}' pag={}): {}", keywords, pagina, ex.getMessage());
        throw new BdnsException("BDNS búsqueda no disponible tras reintentos: " + keywords, ex);
    }

    // ── Detalle enriquecido de una convocatoria ──────────────────────────────


    // @inferido — nivel1/nivel2 son parámetros observados en el portal Angular de BDNS, no documentados oficialmente
    public List<ConvocatoriaDTO> buscarPorFiltros(FiltrosBdns filtros) {
        if (filtros == null || !filtros.tieneAlgunFiltro()) {
            log.warn("BDNS buscarPorFiltros: filtros vacíos, usando búsqueda genérica");
            return buscarPorTexto("subvencion pyme", 0, 20);
        }

        log.info("BDNS buscarPorFiltros: descripcion='{}' ccaa='{}'",
                filtros.descripcion(), filtros.nivel2());

        // Búsqueda principal
        List<ConvocatoriaDTO> resultados = ejecutarBusquedaFiltrada(filtros);
        log.info("BDNS filtros principal: {} resultados", resultados.size());

        // Fallback progresivo si pocos resultados
        if (resultados.size() < MIN_RESULTADOS_FALLBACK) {
            // Nivel 1 de fallback: quitar descripción, mantener territorio
            if (filtros.descripcion() != null && filtros.nivel2() != null) {
                log.info("BDNS fallback nivel 1: relajando descripción (manteniendo CCAA='{}')", filtros.nivel2());
                List<ConvocatoriaDTO> fallback1 = ejecutarBusquedaFiltrada(filtros.sinDescripcion());
                resultados = combinarYDeduplicar(resultados, fallback1);
                log.info("BDNS tras fallback 1: {} resultados", resultados.size());
            }

            // Nivel 2 de fallback: quitar territorio, solo descripción
            if (resultados.size() < MIN_RESULTADOS_FALLBACK && filtros.nivel2() != null) {
                log.info("BDNS fallback nivel 2: relajando territorio (solo descripcion='{}')", filtros.descripcion());
                List<ConvocatoriaDTO> fallback2 = ejecutarBusquedaFiltrada(filtros.sinTerritorio());
                resultados = combinarYDeduplicar(resultados, fallback2);
                log.info("BDNS tras fallback 2: {} resultados", resultados.size());
            }
        }

        return resultados;
    }

    /** Mínimo de resultados antes de activar el fallback progresivo. */
    private static final int MIN_RESULTADOS_FALLBACK = 3;

    /** Tamaño de página máximo soportado por la API BDNS. */
    private static final int TAM_PAG_BDNS = 50;

    /** Número máximo de páginas a recorrer por cada llamada territorial (ESTADO / AUTONOMICA). */
    private static final int MAX_PAGINAS = 3;

    /**
     * Ejecuta la búsqueda real contra la API BDNS según los filtros.
     * Si hay ccaa, hace doble búsqueda paralela ESTADO+AUTONOMICA.
     * <p>
     * Multipaginación: recorre hasta {@link #MAX_PAGINAS} páginas de {@link #TAM_PAG_BDNS}
     * resultados cada una, por cada nivel territorial, para recuperar un volumen
     * alto de candidatas coherente con las ~615K convocatorias de la BDNS.
     */
    private List<ConvocatoriaDTO> ejecutarBusquedaFiltrada(FiltrosBdns filtros) {
        String ccaa = filtros.nivel2();
        String desc = filtros.descripcion();

        // Si hay CCAA → doble búsqueda paralela multipágina
        if (ccaa != null) {
            List<ConvocatoriaDTO> combinadas = new CopyOnWriteArrayList<>();
            String ccaaEncoded = URLEncoder.encode(ccaa, StandardCharsets.UTF_8);

            @SuppressWarnings("resource")
            ExecutorService executor = Executors.newCachedThreadPool();
            try {
                // Llamada A: convocatorias estatales (hasta MAX_PAGINAS páginas)
                CompletableFuture<Void> futuroEstatal = CompletableFuture.runAsync(() -> {
                    for (int pag = 0; pag < MAX_PAGINAS; pag++) {
                        try {
                            String url = BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag=" + pag + "&tamPag=" + TAM_PAG_BDNS + "&nivel1=ESTADO";
                            if (desc != null) url += "&descripcion=" + URLEncoder.encode(desc, StandardCharsets.UTF_8) + "&descripcionTipoBusqueda=1";
                            @SuppressWarnings("unchecked")
                            Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                            if (respuesta != null) {
                                List<ConvocatoriaDTO> pagina = mapearRespuesta(respuesta);
                                combinadas.addAll(pagina);
                                // Si esta página devolvió menos de TAM_PAG_BDNS, no hay más páginas
                                if (pagina.size() < TAM_PAG_BDNS) break;
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            log.warn("BDNS filtros ESTADO pag={}: {}", pag, e.getMessage());
                            break;
                        }
                    }
                }, executor);

                // Llamada B: convocatorias autonómicas de la CCAA (hasta MAX_PAGINAS páginas)
                CompletableFuture<Void> futuroAutonomica = CompletableFuture.runAsync(() -> {
                    for (int pag = 0; pag < MAX_PAGINAS; pag++) {
                        try {
                            String url = BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag=" + pag + "&tamPag=" + TAM_PAG_BDNS + "&nivel1=AUTONOMICA&nivel2=" + ccaaEncoded;
                            if (desc != null) url += "&descripcion=" + URLEncoder.encode(desc, StandardCharsets.UTF_8) + "&descripcionTipoBusqueda=1";
                            @SuppressWarnings("unchecked")
                            Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                            if (respuesta != null) {
                                List<ConvocatoriaDTO> pagina = mapearRespuesta(respuesta);
                                combinadas.addAll(pagina);
                                if (pagina.size() < TAM_PAG_BDNS) break;
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            log.warn("BDNS filtros AUTONOMICA ccaa='{}' pag={}: {}", ccaa, pag, e.getMessage());
                            break;
                        }
                    }
                }, executor);

                CompletableFuture.allOf(futuroEstatal, futuroAutonomica).join();
            } finally {
                executor.shutdown();
            }

            return deduplicarPorIdBdns(combinadas);
        }

        // Sin CCAA → búsqueda multipágina con descripción
        if (desc != null) {
            return buscarMultipagina(desc);
        }

        // Ni CCAA ni descripción → genérico
        return buscarMultipagina("subvencion pyme");
    }

    /**
     * Busca en BDNS recorriendo hasta {@link #MAX_PAGINAS} páginas para obtener más resultados.
     */
    private List<ConvocatoriaDTO> buscarMultipagina(String keywords) {
        List<ConvocatoriaDTO> todos = new ArrayList<>();
        for (int pag = 0; pag < MAX_PAGINAS; pag++) {
            List<ConvocatoriaDTO> pagina = buscarPorTexto(keywords, pag, TAM_PAG_BDNS);
            todos.addAll(pagina);
            if (pagina.size() < TAM_PAG_BDNS) break;
        }
        return todos;
    }

    /**
     * Combina dos listas y deduplica por idBdns.
     */
    private List<ConvocatoriaDTO> combinarYDeduplicar(List<ConvocatoriaDTO> lista1, List<ConvocatoriaDTO> lista2) {
        List<ConvocatoriaDTO> combinada = new ArrayList<>(lista1);
        combinada.addAll(lista2);
        return deduplicarPorIdBdns(combinada);
    }

    /**
     * Deduplica una lista de ConvocatoriaDTO por idBdns.
     */
    private List<ConvocatoriaDTO> deduplicarPorIdBdns(List<ConvocatoriaDTO> lista) {
        Set<String> vistos = new HashSet<>();
        List<ConvocatoriaDTO> resultado = new ArrayList<>();
        for (ConvocatoriaDTO dto : lista) {
            if (dto.getIdBdns() != null && !dto.getIdBdns().isBlank()) {
                if (vistos.contains(dto.getIdBdns())) continue;
                vistos.add(dto.getIdBdns());
            }
            resultado.add(dto);
        }
        return resultado;
    }

    /**
     * Obtiene el texto enriquecido de una convocatoria BDNS a partir de su ID interno.
     * Llama al endpoint de detalle de la API BDNS y extrae todos los campos de texto
     * relevantes (objeto, beneficiarios, bases reguladoras, requisitos, dotación...).
     * Este texto se pasa a OpenAI para que la guía sea precisa y específica.
     *
     * @param idBdns ID interno de la convocatoria en BDNS (campo "id" del JSON)
     * @return texto concatenado con todos los campos relevantes, o null si no disponible
     */
    public String obtenerDetalleTexto(String idBdns) {
        if (idBdns == null || idBdns.isBlank()) return null;

        // Comprobar caché (TTL 1 hora)
        CachedDetalle cached = cacheDetalle.get(idBdns);
        if (cached != null && Duration.between(cached.savedAt(), Instant.now()).toMillis() < TTL_DETALLE_MS) {
            log.debug("BDNS detalle id={}: recuperado de caché", idBdns);
            return cached.texto();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> detalle = restClient.get()
                    .uri("https://www.infosubvenciones.es/bdnstrans/api/convocatorias/" + idBdns)
                    .retrieve()
                    .body(Map.class);

            if (detalle == null) return null;

            StringBuilder texto = new StringBuilder();
            // Campos de texto enriquecido que devuelve la API de detalle BDNS
            appendCampo(texto, "Objeto",          detalle, "objeto", "descripcionObjeto", "finalidad");
            appendCampo(texto, "Beneficiarios",   detalle, "beneficiarios", "tiposBeneficiarios");
            appendCampo(texto, "Requisitos",      detalle, "requisitos", "condicionesAcceso", "requisitosParticipacion");
            appendCampo(texto, "Dotación",        detalle, "dotacion", "presupuestoTotal", "importeTotal");
            appendCampo(texto, "Bases reguladoras", detalle, "basesReguladoras", "normativa");
            appendCampo(texto, "Plazo solicitud", detalle, "plazoSolicitudes", "plazoPresentacion");
            appendCampo(texto, "Procedimiento",   detalle, "procedimiento", "formaPresentacion");
            appendCampo(texto, "Documentación",   detalle, "documentacion", "documentosRequeridos");

            String resultado = texto.toString().trim();
            log.debug("BDNS detalle id={}: {} chars extraídos", idBdns, resultado.length());
            String valor = resultado.isEmpty() ? null : resultado;

            // Guardar en caché (incluso null para evitar re-intentos innecesarios)
            cacheDetalle.put(idBdns, new CachedDetalle(valor, Instant.now()));
            return valor;

        } catch (Exception e) {
            log.debug("BDNS detalle no disponible para id={}: {}", idBdns, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void appendCampo(StringBuilder sb, String etiqueta, Map<String, Object> mapa, String... claves) {
        for (String clave : claves) {
            Object val = mapa.get(clave);
            if (val == null) continue;
            String texto = extraerTexto(val);
            if (!texto.isBlank()) {
                sb.append(etiqueta).append(": ").append(texto.trim()).append("\n");
                return; // con el primer campo encontrado es suficiente
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extraerTexto(Object val) {
        if (val instanceof String s) return s;
        if (val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            list.forEach(item -> {
                if (item instanceof String s) sb.append(s).append("; ");
                else if (item instanceof Map<?,?> m) {
                    // Intentar campos de texto comunes en objetos anidados
                    for (String campo : new String[]{"descripcion", "nombre", "texto", "valor"}) {
                        Object v = m.get(campo);
                        if (v instanceof String s && !s.isBlank()) { sb.append(s).append("; "); break; }
                    }
                }
            });
            return sb.toString();
        }
        if (val instanceof Map<?,?> m) {
            for (String campo : new String[]{"descripcion", "nombre", "texto", "valor"}) {
                Object v = m.get(campo);
                if (v instanceof String s && !s.isBlank()) return s;
            }
        }
        return val.toString();
    }



    @SuppressWarnings("unchecked")
    private List<ConvocatoriaDTO> mapearRespuesta(Map<String, Object> respuesta) {
        List<ConvocatoriaDTO> resultado = new ArrayList<>();

        Object contenido = respuesta.get("content");
        if (!(contenido instanceof List<?> lista)) {
            log.warn("BDNS: respuesta sin campo 'content'. Keys: {}", respuesta.keySet());
            return resultado;
        }

        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> conv)) continue;
            try {
                resultado.add(mapearConvocatoria((Map<String, Object>) conv));
            } catch (Exception e) {
                log.debug("BDNS: error mapeando item: {}", e.getMessage());
            }
        }

        log.info("BDNS API: {} convocatorias mapeadas de esta página", resultado.size());
        return resultado;
    }

    /**
     * Mapea un objeto JSON de la BDNS a ConvocatoriaDTO.
     * <p>
     * Campos de la API BDNS:
     * - id: ID interno BDNS
     * - descripcion: título/descripción de la convocatoria
     * - numeroConvocatoria: código BDNS
     * - nivel1: ámbito (ESTADO, AUTONOMICA, LOCAL, OTROS)
     * - nivel2: organismo / comunidad
     * - nivel3: sub-organismo
     * - fechaRecepcion: fecha de registro en BDNS
     */
    private ConvocatoriaDTO mapearConvocatoria(Map<String, Object> c) {
        ConvocatoriaDTO dto = new ConvocatoriaDTO();

        // Título: usar descripcion (campo principal de la BDNS)
        dto.setTitulo(getString(c, "descripcion",
                getString(c, "descripcionLeng", "Sin título")));

        // Tipo: derivar del ámbito (nivel1)
        String nivel1 = getString(c, "nivel1", "");
        dto.setTipo(mapearTipo(nivel1));

        // Sector: la API BDNS no devuelve sector directamente
        dto.setSector(null);

        // Ubicación: nivel2 contiene la comunidad/organismo
        dto.setUbicacion(mapearUbicacion(nivel1, getString(c, "nivel2", null)));

        // Organismo y fuente
        String organismo = getString(c, "nivel3",
                getString(c, "nivel2", "BDNS"));
        dto.setOrganismo(organismo);
        dto.setFuente("BDNS – " + organismo);

        // ID interno BDNS — necesario para obtener el detalle completo
        String idBdns = getString(c, "id", null);
        String numConv = getString(c, "numeroConvocatoria", null);
        if (idBdns != null) {
            dto.setIdBdns(idBdns);
        }
        if (numConv != null && !numConv.isBlank()) {
            dto.setNumeroConvocatoria(numConv);
            // La SPA Angular del portal usa el numeroConvocatoria en la URL de la ficha
            dto.setUrlOficial("https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv);
        } else if (idBdns != null) {
            // Fallback: buscar por ID interno (puede dar "Error al obtener datos" en la SPA)
            dto.setUrlOficial("https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + idBdns);
        }

        // Fecha de publicación: fechaRecepcion = fecha de registro en BDNS
        String fechaRecepcion = getString(c, "fechaRecepcion", null);
        if (fechaRecepcion != null) {
            try {
                dto.setFechaPublicacion(LocalDate.parse(fechaRecepcion.substring(0, 10)));
            } catch (Exception e) {
                log.debug("BDNS: no se pudo parsear fechaRecepcion: {}", fechaRecepcion);
            }
        }

        // Descripcion: mismo campo que el título en BDNS, se guarda también como descripción
        dto.setDescripcion(dto.getTitulo());

        // Fecha de cierre: intentar campos reales de plazo (fechaRecepcion es la de REGISTRO, no cierre)
        String fechaCierre = getString(c, "fechaFinSolicitud",
                getString(c, "fechaCierre",
                        getString(c, "plazoSolicitudes", null)));
        if (fechaCierre != null) {
            parsearFecha(dto, fechaCierre);
        }
        // Si no hay fecha de cierre conocida, dejar null (convocatoria sin plazo definido = abierta)

        log.debug("BDNS conv: titulo='{}' fechaCierre={} nivel1={} idBdns={} numConv={}",
                dto.getTitulo(), dto.getFechaCierre(), nivel1, idBdns, numConv);
        return dto;
    }

    private String mapearTipo(String nivel1) {
        return switch (nivel1.toUpperCase()) {
            case "ESTADO" -> "Estatal";
            case "AUTONOMICA" -> "Autonómica";
            case "LOCAL" -> "Local";
            case "OTROS" -> "Otros organismos";
            default -> "Subvención";
        };
    }

    private String mapearUbicacion(String nivel1, String nivel2) {
        if ("ESTADO".equalsIgnoreCase(nivel1)) return "Nacional";
        if (nivel2 != null && !nivel2.isBlank()) return nivel2;
        return "Nacional";
    }

    private void parsearFecha(ConvocatoriaDTO dto, String fechaStr) {
        if (fechaStr == null || fechaStr.isBlank()) return;
        try {
            // Formato yyyy-MM-dd (lo que devuelve la API)
            dto.setFechaCierre(LocalDate.parse(fechaStr.substring(0, 10)));
        } catch (Exception e) {
            log.debug("BDNS: no se pudo parsear fecha: {}", fechaStr);
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        String s = val.toString().trim();
        return s.isBlank() ? defaultVal : s;
    }

    // ── SSL permisivo para el certificado del gobierno ───────────────────────

    private SimpleClientHttpRequestFactory createSslPermissiveFactory(int connectMs, int readMs) {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] c, String t) {}
                        public void checkServerTrusted(X509Certificate[] c, String t) {}
                    }
            };

            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                        throws java.io.IOException {
                    if (connection instanceof HttpsURLConnection httpsConn) {
                        httpsConn.setSSLSocketFactory(sslCtx.getSocketFactory());
                        httpsConn.setHostnameVerifier((h, s) -> true);
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            };
            factory.setConnectTimeout(connectMs);
            factory.setReadTimeout(readMs);
            return factory;
        } catch (Exception e) {
            log.error("Error configurando SSL para BDNS: {}", e.getMessage());
            SimpleClientHttpRequestFactory fallback = new SimpleClientHttpRequestFactory();
            fallback.setConnectTimeout(connectMs);
            fallback.setReadTimeout(readMs);
            return fallback;
        }
    }

    // ── Excepción propia ─────────────────────────────────────────────────────

    public static class BdnsException extends RuntimeException {
        public BdnsException(String message) { super(message); }
        public BdnsException(String message, Throwable cause) { super(message, cause); }
    }
}
