package com.syntia.ai.service;


import com.syntia.ai.model.ErrorResponse;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.model.dto.FiltrosBdns;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import jakarta.servlet.http.HttpServletRequest;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Slf4j
@RestController
@RequestMapping("/api/bdns")
public class BdnsController {

    private static final String BDNS_BUSQUEDA =
            "https://www.infosubvenciones.es/bdnstrans/api/convocatorias/busqueda";

    private static final int MIN_RESULTADOS_FALLBACK = 3;
    private static final int TAM_PAG_BDNS = 50;
    private static final int MAX_PAGINAS = 3;

    private final RestClient restClient;

    public BdnsController() {
        this.restClient = RestClient.builder()
                .requestFactory(createSslPermissiveFactory())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @GetMapping("/importar")
    public ResponseEntity<List<ConvocatoriaDTO>> importarEndpoint(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return ResponseEntity.ok(importar(pagina, tamano));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ConvocatoriaDTO>> buscarPorTextoEndpoint(
            @RequestParam String keywords,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return ResponseEntity.ok(buscarPorTexto(keywords, pagina, tamano));
    }

    @GetMapping("/buscar-filtrado")
    public ResponseEntity<List<ConvocatoriaDTO>> buscarPorTextoFiltradoEndpoint(
            @RequestParam String keyword,
            @RequestParam(required = false) String ccaa
    ) {
        return ResponseEntity.ok(buscarPorTextoFiltrado(keyword, ccaa));
    }

    @PostMapping("/buscar-filtros")
    public ResponseEntity<List<ConvocatoriaDTO>> buscarPorFiltrosEndpoint(
            @RequestBody FiltrosBdns filtros
    ) {
        return ResponseEntity.ok(buscarPorFiltros(filtros));
    }

    @GetMapping("/detalle/{idBdns}")
    public ResponseEntity<?> obtenerDetalleEndpoint(@PathVariable String idBdns) {
        String detalle = obtenerDetalleTexto(idBdns);

        if (detalle == null || detalle.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "mensaje", "No se encontró detalle para la convocatoria",
                    "idBdns", idBdns
            ));
        }

        return ResponseEntity.ok(Map.of(
                "idBdns", idBdns,
                "detalle", detalle
        ));
    }

    public List<ConvocatoriaDTO> importar(int pagina, int tamano) {
        log.info("Consultando API BDNS: pagina={} tamano={}", pagina, tamano);

        @SuppressWarnings("unchecked")
        Map<String, Object> respuesta = restClient.get()
                .uri(BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag={pag}&tamPag={tam}",
                        pagina, Math.min(tamano, 50))
                .retrieve()
                .body(Map.class);

        if (respuesta == null) {
            throw new BdnsException("La API de BDNS devolvió una respuesta vacía");
        }

        return mapearRespuesta(respuesta);
    }

    public List<ConvocatoriaDTO> buscarPorTexto(String keywords, int pagina, int tamano) {
        log.info("BDNS búsqueda por texto: '{}' pagina={} tamano={}", keywords, pagina, tamano);

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

        return mapearRespuesta(respuesta);
    }

    public List<ConvocatoriaDTO> buscarPorTextoFiltrado(String keyword, String ccaa) {
        if (ccaa == null || ccaa.isBlank()) {
            return buscarPorTexto(keyword, 0, 15);
        }

        log.info("BDNS búsqueda filtrada: keyword='{}' ccaa='{}'", keyword, ccaa);

        List<ConvocatoriaDTO> combinadas = new CopyOnWriteArrayList<>();
        String ccaaEncoded = URLEncoder.encode(ccaa, StandardCharsets.UTF_8);

        @SuppressWarnings("resource")
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
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
                } catch (Exception e) {
                    log.warn("Error en búsqueda BDNS ESTADO keyword='{}': {}", keyword, e.getMessage());
                }
            }, executor);

            CompletableFuture<Void> futuroAutonomica = CompletableFuture.runAsync(() -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respuesta = restClient.get()
                            .uri(BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag=0&tamPag=10" +
                                    "&descripcion=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) +
                                    "&descripcionTipoBusqueda=1&vigente=true" +
                                    "&nivel1=AUTONOMICA&nivel2=" + ccaaEncoded)
                            .retrieve()
                            .body(Map.class);
                    if (respuesta != null) {
                        combinadas.addAll(mapearRespuesta(respuesta));
                    }
                } catch (Exception e) {
                    log.warn("Error en búsqueda BDNS AUTONOMICA keyword='{}' ccaa='{}': {}", keyword, ccaa, e.getMessage());
                }
            }, executor);

            CompletableFuture.allOf(futuroEstatal, futuroAutonomica).join();
        } finally {
            executor.shutdown();
        }

        return deduplicarPorIdBdns(combinadas);
    }

    public List<ConvocatoriaDTO> buscarPorFiltros(FiltrosBdns filtros) {
        if (filtros == null || !filtros.tieneAlgunFiltro()) {
            log.warn("BDNS buscarPorFiltros: filtros vacíos, usando búsqueda genérica");
            return buscarPorTexto("subvencion pyme", 0, 20);
        }

        log.info("BDNS buscarPorFiltros: descripcion='{}' ccaa='{}'",
                filtros.descripcion(), filtros.nivel2());

        List<ConvocatoriaDTO> resultados = ejecutarBusquedaFiltrada(filtros);

        if (resultados.size() < MIN_RESULTADOS_FALLBACK) {
            if (filtros.descripcion() != null && filtros.nivel2() != null) {
                List<ConvocatoriaDTO> fallback1 = ejecutarBusquedaFiltrada(filtros.sinDescripcion());
                resultados = combinarYDeduplicar(resultados, fallback1);
            }

            if (resultados.size() < MIN_RESULTADOS_FALLBACK && filtros.nivel2() != null) {
                List<ConvocatoriaDTO> fallback2 = ejecutarBusquedaFiltrada(filtros.sinTerritorio());
                resultados = combinarYDeduplicar(resultados, fallback2);
            }
        }

        return resultados;
    }

    private List<ConvocatoriaDTO> ejecutarBusquedaFiltrada(FiltrosBdns filtros) {
        String ccaa = filtros.nivel2();
        String desc = filtros.descripcion();

        if (ccaa != null && !ccaa.isBlank()) {
            List<ConvocatoriaDTO> combinadas = new CopyOnWriteArrayList<>();
            String ccaaEncoded = URLEncoder.encode(ccaa, StandardCharsets.UTF_8);

            @SuppressWarnings("resource")
            ExecutorService executor = Executors.newCachedThreadPool();
            try {
                CompletableFuture<Void> futuroEstatal = CompletableFuture.runAsync(() -> {
                    for (int pag = 0; pag < MAX_PAGINAS; pag++) {
                        try {
                            String url = BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag=" + pag +
                                    "&tamPag=" + TAM_PAG_BDNS + "&nivel1=ESTADO";
                            if (desc != null && !desc.isBlank()) {
                                url += "&descripcion=" + URLEncoder.encode(desc, StandardCharsets.UTF_8) +
                                        "&descripcionTipoBusqueda=1";
                            }

                            @SuppressWarnings("unchecked")
                            Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                            if (respuesta != null) {
                                List<ConvocatoriaDTO> pagina = mapearRespuesta(respuesta);
                                combinadas.addAll(pagina);
                                if (pagina.size() < TAM_PAG_BDNS) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            log.warn("BDNS filtros ESTADO pag={}: {}", pag, e.getMessage());
                            break;
                        }
                    }
                }, executor);

                CompletableFuture<Void> futuroAutonomica = CompletableFuture.runAsync(() -> {
                    for (int pag = 0; pag < MAX_PAGINAS; pag++) {
                        try {
                            String url = BDNS_BUSQUEDA + "?vpn=GE&vln=es&numPag=" + pag +
                                    "&tamPag=" + TAM_PAG_BDNS +
                                    "&nivel1=AUTONOMICA&nivel2=" + ccaaEncoded;
                            if (desc != null && !desc.isBlank()) {
                                url += "&descripcion=" + URLEncoder.encode(desc, StandardCharsets.UTF_8) +
                                        "&descripcionTipoBusqueda=1";
                            }

                            @SuppressWarnings("unchecked")
                            Map<String, Object> respuesta = restClient.get().uri(url).retrieve().body(Map.class);
                            if (respuesta != null) {
                                List<ConvocatoriaDTO> pagina = mapearRespuesta(respuesta);
                                combinadas.addAll(pagina);
                                if (pagina.size() < TAM_PAG_BDNS) {
                                    break;
                                }
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

        if (desc != null && !desc.isBlank()) {
            return buscarMultipagina(desc);
        }

        return buscarMultipagina("subvencion pyme");
    }

    private List<ConvocatoriaDTO> buscarMultipagina(String keywords) {
        List<ConvocatoriaDTO> todos = new ArrayList<>();
        for (int pag = 0; pag < MAX_PAGINAS; pag++) {
            List<ConvocatoriaDTO> pagina = buscarPorTexto(keywords, pag, TAM_PAG_BDNS);
            todos.addAll(pagina);
            if (pagina.size() < TAM_PAG_BDNS) {
                break;
            }
        }
        return todos;
    }

    private List<ConvocatoriaDTO> combinarYDeduplicar(List<ConvocatoriaDTO> lista1, List<ConvocatoriaDTO> lista2) {
        List<ConvocatoriaDTO> combinada = new ArrayList<>(lista1);
        combinada.addAll(lista2);
        return deduplicarPorIdBdns(combinada);
    }

    private List<ConvocatoriaDTO> deduplicarPorIdBdns(List<ConvocatoriaDTO> lista) {
        Set<String> vistos = new HashSet<>();
        List<ConvocatoriaDTO> resultado = new ArrayList<>();

        for (ConvocatoriaDTO dto : lista) {
            if (dto.getIdBdns() != null && !dto.getIdBdns().isBlank()) {
                if (vistos.contains(dto.getIdBdns())) {
                    continue;
                }
                vistos.add(dto.getIdBdns());
            }
            resultado.add(dto);
        }

        return resultado;
    }

    public String obtenerDetalleTexto(String idBdns) {
        if (idBdns == null || idBdns.isBlank()) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> detalle = restClient.get()
                    .uri("https://www.infosubvenciones.es/bdnstrans/api/convocatorias/" + idBdns)
                    .retrieve()
                    .body(Map.class);

            if (detalle == null) {
                return null;
            }

            StringBuilder texto = new StringBuilder();
            appendCampo(texto, "Objeto", detalle, "objeto", "descripcionObjeto", "finalidad");
            appendCampo(texto, "Beneficiarios", detalle, "beneficiarios", "tiposBeneficiarios");
            appendCampo(texto, "Requisitos", detalle, "requisitos", "condicionesAcceso", "requisitosParticipacion");
            appendCampo(texto, "Dotación", detalle, "dotacion", "presupuestoTotal", "importeTotal");
            appendCampo(texto, "Bases reguladoras", detalle, "basesReguladoras", "normativa");
            appendCampo(texto, "Plazo solicitud", detalle, "plazoSolicitudes", "plazoPresentacion");
            appendCampo(texto, "Procedimiento", detalle, "procedimiento", "formaPresentacion");
            appendCampo(texto, "Documentación", detalle, "documentacion", "documentosRequeridos");

            String resultado = texto.toString().trim();
            return resultado.isEmpty() ? null : resultado;
        } catch (Exception e) {
            log.debug("BDNS detalle no disponible para id={}: {}", idBdns, e.getMessage());
            return null;
        }
    }

    private void appendCampo(StringBuilder sb, String etiqueta, Map<String, Object> mapa, String... claves) {
        for (String clave : claves) {
            Object val = mapa.get(clave);
            if (val == null) {
                continue;
            }
            String texto = extraerTexto(val);
            if (!texto.isBlank()) {
                sb.append(etiqueta).append(": ").append(texto.trim()).append("\n");
                return;
            }
        }
    }

    private String extraerTexto(Object val) {
        if (val instanceof String s) {
            return s;
        }

        if (val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            list.forEach(item -> {
                if (item instanceof String s) {
                    sb.append(s).append("; ");
                } else if (item instanceof Map<?, ?> m) {
                    for (String campo : new String[]{"descripcion", "nombre", "texto", "valor"}) {
                        Object v = m.get(campo);
                        if (v instanceof String s && !s.isBlank()) {
                            sb.append(s).append("; ");
                            break;
                        }
                    }
                }
            });
            return sb.toString();
        }

        if (val instanceof Map<?, ?> m) {
            for (String campo : new String[]{"descripcion", "nombre", "texto", "valor"}) {
                Object v = m.get(campo);
                if (v instanceof String s && !s.isBlank()) {
                    return s;
                }
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
            if (!(item instanceof Map<?, ?> conv)) {
                continue;
            }
            try {
                resultado.add(mapearConvocatoria((Map<String, Object>) conv));
            } catch (Exception e) {
                log.debug("BDNS: error mapeando item: {}", e.getMessage());
            }
        }

        return resultado;
    }

    private ConvocatoriaDTO mapearConvocatoria(Map<String, Object> c) {
        ConvocatoriaDTO dto = new ConvocatoriaDTO();

        dto.setTitulo(getString(c, "descripcion",
                getString(c, "descripcionLeng", "Sin título")));

        String nivel1 = getString(c, "nivel1", "");
        dto.setTipo(mapearTipo(nivel1));
        dto.setSector(null);
        dto.setUbicacion(mapearUbicacion(nivel1, getString(c, "nivel2", null)));

        String organismo = getString(c, "nivel3", getString(c, "nivel2", "BDNS"));
        dto.setFuente("BDNS – " + organismo);

        String idBdns = getString(c, "id", null);
        String numConv = getString(c, "numeroConvocatoria", null);

        if (idBdns != null) {
            dto.setIdBdns(idBdns);
        }

        if (numConv != null && !numConv.isBlank()) {
            dto.setNumeroConvocatoria(numConv);
            dto.setUrlOficial("https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv);
        } else if (idBdns != null) {
            dto.setUrlOficial("https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + idBdns);
        }

        String fechaCierre = getString(c, "fechaFinSolicitud",
                getString(c, "fechaCierre",
                        getString(c, "plazoSolicitudes", null)));

        if (fechaCierre != null) {
            parsearFecha(dto, fechaCierre);
        }

        return dto;
    }

    private String mapearTipo(String nivel1) {
        if (nivel1 == null) {
            return "Subvención";
        }

        return switch (nivel1.toUpperCase()) {
            case "ESTADO" -> "Estatal";
            case "AUTONOMICA" -> "Autonómica";
            case "LOCAL" -> "Local";
            case "OTROS" -> "Otros organismos";
            default -> "Subvención";
        };
    }

    private String mapearUbicacion(String nivel1, String nivel2) {
        if ("ESTADO".equalsIgnoreCase(nivel1)) {
            return "Nacional";
        }
        if (nivel2 != null && !nivel2.isBlank()) {
            return nivel2;
        }
        return "Nacional";
    }

    private void parsearFecha(ConvocatoriaDTO dto, String fechaStr) {
        if (fechaStr == null || fechaStr.isBlank()) {
            return;
        }
        try {
            dto.setFechaCierre(LocalDate.parse(fechaStr.substring(0, 10)));
        } catch (Exception e) {
            log.debug("BDNS: no se pudo parsear fecha: {}", fechaStr);
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        if (val == null) {
            return defaultVal;
        }
        String s = val.toString().trim();
        return s.isBlank() ? defaultVal : s;
    }

    private SimpleClientHttpRequestFactory createSslPermissiveFactory() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] c, String t) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] c, String t) {
                        }
                    }
            };

            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());

            return new SimpleClientHttpRequestFactory() {
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
        } catch (Exception e) {
            log.error("Error configurando SSL para BDNS: {}", e.getMessage());
            return new SimpleClientHttpRequestFactory();
        }
    }

    @ExceptionHandler(BdnsException.class)
    public ResponseEntity<ErrorResponse> handleBdnsException(
            BdnsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    public static class BdnsException extends RuntimeException {
        public BdnsException(String message) {
            super(message);
        }

        public BdnsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
