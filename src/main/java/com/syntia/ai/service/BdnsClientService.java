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
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private static final String BDNS_DETALLE =
            "https://www.infosubvenciones.es/bdnstrans/api/convocatorias";

    private static final String BDNS_REGIONES =
            "https://www.infosubvenciones.es/bdnstrans/api/regiones";

    /** TTL del caché de detalles BDNS: 1 hora. Reduce latencia en guías enriquecidas. */
    private static final long TTL_DETALLE_MS = 3_600_000L;

    private record CachedDetalle(String texto, Instant savedAt) {}
    public record DetalleConvocatoriaBdns(String descripcion, String sector, List<String> tiposBeneficiario) {}

    private final ConcurrentHashMap<String, CachedDetalle> cacheDetalle = new ConcurrentHashMap<>();

    @Value("${bdns.client.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${bdns.client.read-timeout-ms:30000}")
    private int readTimeoutMs;

    private RestClient restClient;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.syntia.ai.service.BdnsRegionMapper bdnsRegionMapper;

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
                .uri(buildBusquedaUrl(null, null, null, null, false, pagina, tamano))
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
    /** Resultado paginado de un eje BDNS: registros mapeados + total de elementos + si es la última página. */
    public record PaginaBdns(List<ConvocatoriaDTO> items, long totalElements, boolean esUltima) {}

    @Retryable(
        retryFor = {ResourceAccessException.class, RestClientException.class},
        noRetryFor = HttpClientErrorException.class,
        maxAttemptsExpression = "${bdns.client.max-reintentos:3}",
        backoff = @Backoff(delayExpression = "${bdns.client.reintento-delay-ms:1500}", multiplier = 2.0)
    )
    public PaginaBdns importarPorEje(String nivel1, String nivel2, int pagina, int tamano) {
        String url = buildBusquedaUrl(null, null, null, null, false, pagina, tamano);
        log.debug("BDNS importarPorEje: pag={}", pagina);

        @SuppressWarnings("unchecked")
        Map<String, Object> respuesta = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map.class);

        if (respuesta == null) return new PaginaBdns(List.of(), 0L, true);

        long total = 0L;
        Object totalObj = respuesta.get("totalElements");
        if (totalObj instanceof Number n) total = n.longValue();

        boolean esUltima = Boolean.TRUE.equals(respuesta.get("last"));

        return new PaginaBdns(mapearRespuesta(respuesta), total, esUltima);
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
                .uri(buildBusquedaUrl(keywords, null, null, null, true, pagina, tamano))
                .retrieve()
                .body(Map.class);

        if (respuesta == null) {
            throw new BdnsException("BDNS devolvió respuesta vacía para búsqueda: " + keywords);
        }

        Object totalObj = respuesta.get("totalElements");
        log.info("BDNS búsqueda '{}': totalElements={}", keywords, totalObj);

        return mapearRespuesta(respuesta);
    }

    public List<ConvocatoriaDTO> buscarPorTextoFiltrado(String keyword, Integer regionId) {
        if (regionId == null) {
            return buscarPorTexto(keyword, 0, 15);
        }

        log.info("BDNS búsqueda filtrada: keyword='{}' regionId={}", keyword, regionId);

        List<ConvocatoriaDTO> combinadas = new CopyOnWriteArrayList<>();
        @SuppressWarnings("resource")
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            CompletableFuture<Void> futuroEstatal = CompletableFuture.runAsync(() -> {
                try {
                    String url = buildBusquedaUrl(keyword, "C", null, null, true, 0, 10);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                    if (respuesta != null) combinadas.addAll(mapearRespuesta(respuesta));
                } catch (Exception e) {
                    log.warn("BDNS filtrada ESTADO keyword='{}': {}", keyword, e.getMessage());
                }
            }, executor);

            CompletableFuture<Void> futuroAutonomica = CompletableFuture.runAsync(() -> {
                try {
                    String url = buildBusquedaUrl(keyword, "A", regionId, null, true, 0, 10);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                    if (respuesta != null) combinadas.addAll(mapearRespuesta(respuesta));
                } catch (Exception e) {
                    log.warn("BDNS filtrada AUTONOMICA keyword='{}' regionId={}: {}", keyword, regionId, e.getMessage());
                }
            }, executor);

            CompletableFuture.allOf(futuroEstatal, futuroAutonomica).join();
        } finally {
            executor.shutdown();
        }

        return deduplicarPorIdBdns(combinadas);
    }

    // ── Catálogo de regiones ─────────────────────────────────────────────────

    /**
     * Descarga el árbol jerárquico completo de regiones del catálogo BDNS.
     * Devuelve la lista aplanada: cada entrada tiene id, descripcion y parentId (null para raíces).
     */
    public List<RegionItem> fetchRegiones() {
        log.info("Descargando catálogo de regiones desde BDNS...");
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raices = restClient.get()
                    .uri(BDNS_REGIONES)
                    .retrieve()
                    .body(List.class);

            if (raices == null || raices.isEmpty()) {
                log.warn("BDNS /regiones devolvió lista vacía");
                return List.of();
            }

            List<RegionItem> resultado = new ArrayList<>();
            for (Object r : raices) {
                if (r instanceof Map<?,?> nodo) {
                    //noinspection unchecked
                    aplanarNodo((Map<String, Object>) nodo, null, resultado);
                }
            }
            log.info("BDNS regiones: {} nodos cargados", resultado.size());
            return resultado;
        } catch (Exception e) {
            log.error("Error descargando regiones BDNS: {}", e.getMessage());
            throw new BdnsException("No se pudo obtener el catálogo de regiones BDNS", e);
        }
    }

    /** Nodo aplanado del árbol de regiones BDNS. */
    public record RegionItem(Long id, String descripcion, Long parentId) {}

    @SuppressWarnings("unchecked")
    private void aplanarNodo(Map<String, Object> nodo, Long parentId, List<RegionItem> acumulador) {
        Object idObj = nodo.get("id");
        Object descObj = nodo.get("descripcion");
        if (idObj == null || descObj == null) return;

        Long id = ((Number) idObj).longValue();
        String descripcion = descObj.toString();
        acumulador.add(new RegionItem(id, descripcion, parentId));

        Object childrenObj = nodo.get("children");
        if (childrenObj instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?,?> childNodo) {
                    aplanarNodo((Map<String, Object>) childNodo, id, acumulador);
                }
            }
        }
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

        log.info("BDNS buscarPorFiltros: descripcion='{}' regionId={}",
                filtros.descripcion(), filtros.regionId());

        // Búsqueda principal
        List<ConvocatoriaDTO> resultados = ejecutarBusquedaFiltrada(filtros);
        log.info("BDNS filtros principal: {} resultados", resultados.size());

        // Fallback progresivo si pocos resultados
        if (resultados.size() < MIN_RESULTADOS_FALLBACK) {
            // Nivel 1 de fallback: quitar descripción, mantener territorio
            if (filtros.descripcion() != null && filtros.regionId() != null) {
                log.info("BDNS fallback nivel 1: relajando descripción (manteniendo regionId={})", filtros.regionId());
                List<ConvocatoriaDTO> fallback1 = ejecutarBusquedaFiltrada(filtros.sinDescripcion());
                resultados = combinarYDeduplicar(resultados, fallback1);
                log.info("BDNS tras fallback 1: {} resultados", resultados.size());
            }

            // Nivel 2 de fallback: si la región tiene pocos resultados, mostrar subvenciones del ESTADO
            if (resultados.size() < MIN_RESULTADOS_FALLBACK && filtros.regionId() != null) {
                log.info("BDNS fallback nivel 2: buscando convocatorias Nacionales para descripcion='{}'", filtros.descripcion());
                List<ConvocatoriaDTO> fallback2 = new ArrayList<>();
                for (int pag = 0; pag < MAX_PAGINAS; pag++) {
                    try {
                        String url = buildBusquedaUrl(filtros.descripcion(), "C", null, filtros.finalidadId(), true, pag, TAM_PAG_BDNS);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resp = restClient.get().uri(url).retrieve().body(Map.class);
                        if (resp != null) {
                            List<ConvocatoriaDTO> pagina = mapearRespuesta(resp);
                            fallback2.addAll(pagina);
                            if (pagina.size() < TAM_PAG_BDNS) break;
                        } else break;
                    } catch (Exception e) { break; }
                }
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
        Integer regionId = filtros.regionId();
        Integer finalidadId = filtros.finalidadId();
        String desc = filtros.descripcion();

        if (regionId != null) {
            List<ConvocatoriaDTO> combinadas = new CopyOnWriteArrayList<>();
            @SuppressWarnings("resource")
            ExecutorService executor = Executors.newCachedThreadPool();
            try {
                CompletableFuture<Void> futuroEstatal = CompletableFuture.runAsync(() -> {
                    for (int pag = 0; pag < MAX_PAGINAS; pag++) {
                        try {
                            String url = buildBusquedaUrl(desc, "C", null, finalidadId, true, pag, TAM_PAG_BDNS);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                            if (respuesta != null) {
                                List<ConvocatoriaDTO> pagina = mapearRespuesta(respuesta);
                                combinadas.addAll(pagina);
                                if (pagina.size() < TAM_PAG_BDNS) break;
                            } else break;
                        } catch (Exception e) {
                            log.warn("BDNS filtros ESTADO pag={}: {}", pag, e.getMessage());
                            break;
                        }
                    }
                }, executor);

                CompletableFuture<Void> futuroAutonomica = CompletableFuture.runAsync(() -> {
                    for (int pag = 0; pag < MAX_PAGINAS; pag++) {
                        try {
                            String url = buildBusquedaUrl(desc, "A", regionId, finalidadId, true, pag, TAM_PAG_BDNS);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                            if (respuesta != null) {
                                List<ConvocatoriaDTO> pagina = mapearRespuesta(respuesta);
                                combinadas.addAll(pagina);
                                if (pagina.size() < TAM_PAG_BDNS) break;
                            } else break;
                        } catch (Exception e) {
                            log.warn("BDNS filtros AUTONOMICA regionId={} pag={}: {}", regionId, pag, e.getMessage());
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

        if (desc != null) {
            return buscarMultipagina(desc);
        }

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
     * Obtiene el texto enriquecido de una convocatoria BDNS a partir de su numeroConvocatoria.
     * Llama al endpoint de detalle de la API BDNS y extrae todos los campos de texto
     * relevantes (objeto, finalidad, sectores, beneficiarios, dotación, plazos...).
     * Este texto se pasa a OpenAI para que la guía sea precisa y específica.
     *
     * @param numeroConvocatoria código de convocatoria BDNS (ej: "406718")
     * @return texto concatenado con todos los campos relevantes, o null si no disponible
     */
    public String obtenerDetalleTexto(String numeroConvocatoria) {
        if (numeroConvocatoria == null || numeroConvocatoria.isBlank()) return null;

        // Check cache (TTL 1 hour)
        CachedDetalle cached = cacheDetalle.get(numeroConvocatoria);
        if (cached != null && Duration.between(cached.savedAt(), Instant.now()).toMillis() < TTL_DETALLE_MS) {
            log.debug("BDNS detalle numConv={}: recuperado de caché", numeroConvocatoria);
            return cached.texto();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(BDNS_DETALLE)
                    .queryParam("vpd", "GE")
                    .queryParam("numConv", numeroConvocatoria)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> detalle = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (detalle == null) return null;

            StringBuilder texto = new StringBuilder();

            // Título/descripción
            appendCampo(texto, "Objeto", detalle, "descripcion");

            // Finalidad
            appendCampo(texto, "Finalidad", detalle, "descripcionFinalidad");

            // Sectores (array de objetos con descripcion)
            Object sectoresObj = detalle.get("sectores");
            if (sectoresObj instanceof List<?> sectList && !sectList.isEmpty()) {
                StringBuilder secSb = new StringBuilder();
                for (Object s : sectList) {
                    if (s instanceof Map<?,?> sm) {
                        Object d = sm.get("descripcion");
                        if (d != null) secSb.append(d).append("; ");
                    }
                }
                if (!secSb.isEmpty()) texto.append("Sectores: ").append(secSb.toString().trim()).append("\n");
            }

            // Tipos de beneficiarios
            Object bensObj = detalle.get("tiposBeneficiarios");
            if (bensObj instanceof List<?> benList && !benList.isEmpty()) {
                StringBuilder benSb = new StringBuilder();
                for (Object b : benList) {
                    if (b instanceof Map<?,?> bm) {
                        Object d = bm.get("descripcion");
                        if (d != null) benSb.append(d).append("; ");
                    }
                }
                if (!benSb.isEmpty()) texto.append("Beneficiarios: ").append(benSb.toString().trim()).append("\n");
            }

            // Instrumentos
            Object instrObj = detalle.get("instrumentos");
            if (instrObj instanceof List<?> instrList && !instrList.isEmpty()) {
                StringBuilder instrSb = new StringBuilder();
                for (Object i : instrList) {
                    if (i instanceof Map<?,?> im) {
                        Object d = im.get("descripcion");
                        if (d != null) instrSb.append(d).append("; ");
                    }
                }
                if (!instrSb.isEmpty()) texto.append("Instrumentos: ").append(instrSb.toString().trim()).append("\n");
            }

            // Presupuesto
            Object presObj = detalle.get("presupuestoTotal");
            if (presObj instanceof Number n) texto.append("Dotación: ").append(n).append(" €\n");

            // Bases reguladoras
            appendCampo(texto, "Bases reguladoras", detalle, "descripcionBasesReguladoras");

            // Plazos
            appendCampo(texto, "Inicio solicitud", detalle, "fechaInicioSolicitud");
            appendCampo(texto, "Fin solicitud", detalle, "fechaFinSolicitud");

            // Fondos UE
            Object fondosObj = detalle.get("fondos");
            if (fondosObj instanceof List<?> fondList && !fondList.isEmpty()) {
                StringBuilder fondSb = new StringBuilder();
                for (Object f : fondList) {
                    if (f instanceof Map<?,?> fm) {
                        Object d = fm.get("descripcion");
                        if (d != null) fondSb.append(d).append("; ");
                    }
                }
                if (!fondSb.isEmpty()) texto.append("Fondos UE: ").append(fondSb.toString().trim()).append("\n");
            }

            // Texto completo del anuncio (HTML)
            Object anunciosObj = detalle.get("anuncios");
            if (anunciosObj instanceof List<?> anList && !anList.isEmpty()) {
                Object first = anList.get(0);
                if (first instanceof Map<?,?> am) {
                    Object textoAnuncio = am.get("texto");
                    if (textoAnuncio instanceof String s && !s.isBlank()) {
                        // Strip basic HTML tags for readability
                        String stripped = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                        texto.append("Texto convocatoria: ").append(stripped).append("\n");
                    }
                }
            }

            String resultado = texto.toString().trim();
            log.debug("BDNS detalle numConv={}: {} chars extraídos", numeroConvocatoria, resultado.length());
            String valor = resultado.isEmpty() ? null : resultado;

            cacheDetalle.put(numeroConvocatoria, new CachedDetalle(valor, Instant.now()));
            return valor;

        } catch (Exception e) {
            log.debug("BDNS detalle no disponible para numConv={}: {}", numeroConvocatoria, e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene detalle estructurado de BDNS para una convocatoria.
     * Devuelve null si la API no responde o no hay datos utiles.
     */
    public DetalleConvocatoriaBdns obtenerDetalleConvocatoria(String numeroConvocatoria) {
        if (numeroConvocatoria == null || numeroConvocatoria.isBlank()) return null;

        try {
            String url = UriComponentsBuilder.fromHttpUrl(BDNS_DETALLE)
                    .queryParam("vpd", "GE")
                    .queryParam("numConv", numeroConvocatoria)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> detalle = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (detalle == null) return null;

            String descripcion = getString(detalle, "descripcion",
                    getString(detalle, "descripcionLeng", null));
            String sector = null;
            Object sectoresObj = detalle.get("sectores");
            if (sectoresObj instanceof List<?> sectores && !sectores.isEmpty()) {
                Object first = sectores.get(0);
                if (first instanceof Map<?, ?> sm) {
                    Object d = sm.get("descripcion");
                    if (d instanceof String s && !s.isBlank()) {
                        sector = toTitleCase(s);
                    }
                }
            }

            List<String> tiposBeneficiario = extraerDescripcionesLista(detalle.get("tiposBeneficiarios"));
            if ((descripcion == null || descripcion.isBlank())
                    && (sector == null || sector.isBlank())
                    && tiposBeneficiario.isEmpty()) {
                return null;
            }

            return new DetalleConvocatoriaBdns(descripcion, sector, tiposBeneficiario);
        } catch (Exception e) {
            log.debug("BDNS detalle estructurado no disponible para numConv={}: {}", numeroConvocatoria, e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el detalle completo de una convocatoria desde la API BDNS en tiempo real.
     * Devuelve el Map crudo con todos los campos para construir el DTO enriquecido.
     * Devuelve null si la API no responde.
     */
    public Map<String, Object> obtenerDetalleLive(String numeroConvocatoria) {
        if (numeroConvocatoria == null || numeroConvocatoria.isBlank()) return null;

        try {
            String url = UriComponentsBuilder.fromHttpUrl(BDNS_DETALLE)
                    .queryParam("numConv", numeroConvocatoria)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> detalle = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            return detalle;
        } catch (Exception e) {
            log.warn("BDNS detalle live no disponible para numConv={}: {}", numeroConvocatoria, e.getMessage());
            return null;
        }
    }

    /**
     * Enriquece un ConvocatoriaDTO con datos del endpoint de detalle BDNS.
     * Popula: sector real (desde sectores NACE), presupuesto, fechaInicio, fechaCierre, abierto, finalidad.
     * Solo llamar para búsquedas de usuario (no ETL masivo).
     *
     * @param dto DTO a enriquecer, debe tener numeroConvocatoria relleno
     */
    public void enriquecerConDetalle(ConvocatoriaDTO dto) {
        String numConv = dto.getNumeroConvocatoria();
        if (numConv == null || numConv.isBlank()) return;

        try {
            String url = UriComponentsBuilder.fromHttpUrl(BDNS_DETALLE)
                    .queryParam("vpd", "GE")
                    .queryParam("numConv", numConv)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> detalle = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (detalle == null) return;

            // Sector real desde sectores NACE (primer sector disponible)
            Object sectoresObj = detalle.get("sectores");
            if (sectoresObj instanceof List<?> sectList && !sectList.isEmpty()) {
                Object first = sectList.get(0);
                if (first instanceof Map<?,?> sm) {
                    Object d = sm.get("descripcion");
                    if (d instanceof String s && !s.isBlank()) {
                        dto.setSector(toTitleCase(s));
                    }
                }
            }

            // Finalidad (política de gasto)
            Object finalidadObj = detalle.get("descripcionFinalidad");
            if (finalidadObj instanceof String s && !s.isBlank()) {
                dto.setFinalidad(toTitleCase(s));
                // Si no hay sector del NACE, usar finalidad como sector
                if (dto.getSector() == null) dto.setSector(toTitleCase(s));
            }

            // Presupuesto
            Object presObj = detalle.get("presupuestoTotal");
            if (presObj instanceof Number n) dto.setPresupuesto(n.doubleValue());

            // Fechas de solicitud
            String fechaInicio = getString(detalle, "fechaInicioSolicitud", null);
            if (fechaInicio != null) {
                try { dto.setFechaInicio(LocalDate.parse(fechaInicio.substring(0, 10))); } catch (Exception ignored) {}
            }
            String fechaFin = getString(detalle, "fechaFinSolicitud", null);
            if (fechaFin != null) {
                try { dto.setFechaCierre(LocalDate.parse(fechaFin.substring(0, 10))); } catch (Exception ignored) {}
            }

            // Abierto: calcular de forma fiable usando fechas + flag API
            // La API devuelve abierto=true para solicitudes sin fecha fin (plazo indefinido)
            // Pero no refleja si la fecha ya pasó — lo calculamos nosotros
            Object abiertoObj = detalle.get("abierto");
            if (Boolean.TRUE.equals(abiertoObj)) {
                dto.setAbierto(true);
            } else if (dto.getFechaCierre() == null) {
                // Sin fecha cierre y sin flag = abierta por defecto
                dto.setAbierto(true);
            } else {
                // Hay fecha cierre: abierta solo si no ha pasado
                dto.setAbierto(!dto.getFechaCierre().isBefore(LocalDate.now()));
            }

            // Region ID desde el detalle usando el mapper robusto
            if (dto.getRegionId() == null) {
                dto.setRegionId(bdnsRegionMapper.extraerRegionId(detalle));
            }

            // Provincia ID desde el detalle usando el mapper robusto
            if (dto.getProvinciaId() == null) {
                dto.setProvinciaId(bdnsRegionMapper.extraerProvinciaId(detalle));
            }

            // Texto completo del anuncio (HTML stripped)
            Object anunciosObj = detalle.get("anuncios");
            if (anunciosObj instanceof List<?> anList && !anList.isEmpty()) {
                Object first = anList.get(0);
                if (first instanceof Map<?,?> am) {
                    Object textoAnuncio = am.get("texto");
                    if (textoAnuncio instanceof String s && !s.isBlank()) {
                        String stripped = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                        if (!stripped.isBlank()) dto.setTextoCompleto(stripped);
                    }
                }
            }

            // Fecha real de publicación: usar fechaInicioSolicitud si es anterior a fechaRecepcion
            // (fechaRecepcion = fecha de registro en BDNS, puede ser muy posterior a la apertura real)
            if (fechaInicio != null) {
                try {
                    LocalDate inicio = LocalDate.parse(fechaInicio.substring(0, 10));
                    if (dto.getFechaPublicacion() == null || inicio.isBefore(dto.getFechaPublicacion())) {
                        dto.setFechaPublicacion(inicio);
                    }
                } catch (Exception ignored) {}
            }

            log.debug("BDNS enriquecer numConv={}: sector='{}' presupuesto={} abierto={} finalidad='{}' regionId={}",
                    numConv, dto.getSector(), dto.getPresupuesto(), dto.getAbierto(), dto.getFinalidad(), dto.getRegionId());

        } catch (Exception e) {
            log.debug("BDNS enriquecer no disponible para numConv={}: {}", numConv, e.getMessage());
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

        // DEBUG temporal — muestra todas las claves del primer item para verificar nombres de campos
        log.debug("BDNS raw keys: {}", c.keySet());
        log.debug("BDNS raw nivel1={} nivel2={} nivel3={}", c.get("nivel1"), c.get("nivel2"), c.get("nivel3"));

        // Título: usar descripcion (campo principal de la BDNS)
        dto.setTitulo(getString(c, "descripcion",
                getString(c, "descripcionLeng", "Sin título")));

        // Tipo: derivar del ámbito (nivel1)
        String nivel1 = getString(c, "nivel1", "");
        dto.setTipo(mapearTipo(nivel1));

        // Sector: inferido por palabras clave de la descripción
        dto.setSector(inferirSector(getString(c, "descripcion", null)));

        // Ubicación: nivel2 contiene la comunidad/organismo
        dto.setUbicacion(mapearUbicacion(nivel1, getString(c, "nivel2", null)));

        // Usamos el BdnsRegionMapper optimizado para extraer y mapear el region ID
        dto.setRegionId(bdnsRegionMapper.extraerRegionId(c));

        // Usamos el BdnsRegionMapper optimizado para extraer la provincia ID
        dto.setProvinciaId(bdnsRegionMapper.extraerProvinciaId(c));

        // MRR: financiación NextGenerationEU / Plan de Recuperación
        Object mrrObj = c.get("mrr");
        dto.setMrr(Boolean.TRUE.equals(mrrObj));

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

        // Fecha de publicación: preferir fechaInicioSolicitud (apertura real del plazo),
        // fallback a fechaRecepcion (fecha de registro en BDNS, menos fiable)
        String fechaInicioSol = getString(c, "fechaInicioSolicitud", null);
        String fechaRecepcion = getString(c, "fechaRecepcion", null);
        String mejorFechaPubl = fechaInicioSol != null ? fechaInicioSol : fechaRecepcion;
        if (mejorFechaPubl != null) {
            try {
                dto.setFechaPublicacion(LocalDate.parse(mejorFechaPubl.substring(0, 10)));
            } catch (Exception e) {
                log.debug("BDNS: no se pudo parsear fecha publicación: {}", mejorFechaPubl);
            }
        }

        // Fecha de inicio de solicitud
        if (fechaInicioSol != null) {
            try {
                dto.setFechaInicio(LocalDate.parse(fechaInicioSol.substring(0, 10)));
            } catch (Exception ignored) {}
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
        if (nivel2 != null && !nivel2.isBlank()) return toTitleCase(nivel2);
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
        // Si es un objeto anidado (ej: nivel2 = {"id":26,"descripcion":"MADRID"})
        // intentar extraer el campo "descripcion" o "nombre"
        if (val instanceof Map<?,?> nested) {
            Object desc = nested.get("descripcion");
            if (desc instanceof String s && !s.isBlank()) return s.trim();
            Object nombre = nested.get("nombre");
            if (nombre instanceof String s && !s.isBlank()) return s.trim();
            return defaultVal;
        }
        String s = val.toString().trim();
        return s.isBlank() ? defaultVal : s;
    }

    private List<String> extraerDescripcionesLista(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }

        Set<String> valores = new LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object descripcion = map.get("descripcion");
                if (descripcion != null) {
                    String texto = descripcion.toString().trim();
                    if (!texto.isBlank()) {
                        valores.add(toTitleCase(texto));
                    }
                }
            }
        }
        if (valores.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(valores));
    }

    /**
     * Construye la URL de búsqueda BDNS moderna usando UriComponentsBuilder.
     * Usa {@code vpd=GE} y los parámetros oficiales {@code page}/{@code pageSize}.
     */
    private String buildBusquedaUrl(String descripcion, String tipoAdministracion,
                                     Integer regionId, Integer finalidadId,
                                     boolean vigente, int pagina, int tamano) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(BDNS_BUSQUEDA)
                .queryParam("vpd", "GE")
                .queryParam("page", pagina)
                .queryParam("pageSize", Math.min(tamano, 50));

        if (descripcion != null && !descripcion.isBlank())
            b.queryParam("descripcion", descripcion);
        if (tipoAdministracion != null && !tipoAdministracion.isBlank())
            b.queryParam("tipoAdministracion", tipoAdministracion);
        if (regionId != null)
            b.queryParam("regiones", regionId);
        if (finalidadId != null)
            b.queryParam("finalidad", finalidadId);
        if (vigente)
            b.queryParam("vigente", "true");

        return b.toUriString();
    }

    /** Convierte un string a Title Case ("ILLES BALEARS" → "Illes Balears"). */
    private String toTitleCase(String input) {
        if (input == null || input.isBlank()) return input;
        String[] words = input.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    /** Infiere el sector de una convocatoria a partir de palabras clave en la descripción. */
    private String inferirSector(String descripcion) {
        if (descripcion == null) return null;
        String d = descripcion.toLowerCase();
        if (containsAny(d, "innov", "tecnolog", "digital", "software", "startup", "i+d", "inteligencia artificial")) return "Tecnología";
        if (containsAny(d, "empleo", "contrataci", "desempleo", "inserci laboral"))  return "Empleo";
        if (containsAny(d, "educaci", "formaci", "beca", "universitari"))            return "Educación";
        if (containsAny(d, "energí", "energia", "renovable", "fotovolt", "sostenib")) return "Medioambiente";
        if (containsAny(d, "agri", "ganad", "pesca", "alimentaci", "forestal"))      return "Agricultura";
        if (containsAny(d, "sanidad", "salud", "hospital", "médic", "farmac"))       return "Sanidad";
        if (containsAny(d, "cultur", "patrimoni", "música", "musica", "teatro", "arte")) return "Cultura";
        if (containsAny(d, "deport", "fútbol", "futbol", "atletism", "nataci"))      return "Deporte";
        if (containsAny(d, "social", "familia", "discapacidad", "mayores", "infanc")) return "Social";
        if (containsAny(d, "pyme", "empresa", "comercio", "emprendedor", "autónom")) return "Empresa";
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
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

    // ── Extracción robusta de reglamentos ────────────────────────────────

    /**
     * Extrae reglamentos del detalle BDNS probando múltiples claves y estructuras.
     * Devuelve una lista de descripciones de reglamentos encontrados.
     * Si existe `reglamentos` (plural) como lista, lo usa; si no, prueba `reglamento` (singular)
     * y otras claves alternativas. No usa fallback a anuncios.
     */
    public List<String> extraerReglamentosDelDetalle(Map<String, Object> detalle) {
        if (detalle == null) return List.of();

        // Candidatas ordenadas por probabilidad
        Object[] candidates = {
                detalle.get("reglamentos"),   // plural
                detalle.get("reglamento"),    // singular
                detalle.get("descripcionBasesReguladoras"),
                detalle.get("basesReguladoras"),
                detalle.get("descripcionBases")
        };

        for (Object cand : candidates) {
            List<String> out = extraerDescripcionesDesdeUniversal(cand);
            if (!out.isEmpty()) return out;
        }

        return List.of();
    }

    /**
     * Extrae descripciones desde un objeto que puede ser: String, List, Map, o nada.
     * Soporta listas con objetos que tienen campo 'descripcion'.
     */
    @SuppressWarnings("unchecked")
    private List<String> extraerDescripcionesDesdeUniversal(Object val) {
        if (val == null) return List.of();

        if (val instanceof String s) {
            String trim = s.trim();
            return !trim.isBlank() ? List.of(trim) : List.of();
        }

        if (val instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                if (item instanceof String s) {
                    String trim = s.trim();
                    if (!trim.isBlank()) result.add(trim);
                } else if (item instanceof Map<?, ?> m) {
                    Object desc = m.get("descripcion");
                    if (desc instanceof String s) {
                        String trim = s.trim();
                        if (!trim.isBlank()) result.add(trim);
                    }
                }
            }
            return result;
        }

        if (val instanceof Map<?, ?> m) {
            Object desc = m.get("descripcion");
            if (desc instanceof String s) {
                String trim = s.trim();
                return !trim.isBlank() ? List.of(trim) : List.of();
            }
        }

        return List.of();
    }

    // ── Excepción propia ─────────────────────────────────────────────────────

    public static class BdnsException extends RuntimeException {
        public BdnsException(String message) { super(message); }
        public BdnsException(String message, Throwable cause) { super(message, cause); }
    }
}
