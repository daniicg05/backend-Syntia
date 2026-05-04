package com.syntia.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.util.*;

@Slf4j
@Service
public class BdnsCatalogoClient {

    private static final String BASE = "https://www.infosubvenciones.es/bdnstrans/api";

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_MS = 5_000L;

    @Value("${bdns.client.connect-timeout-ms:10000}") private int connectMs;
    @Value("${bdns.client.read-timeout-ms:30000}")    private int readMs;

    private RestClient restClient;
    private RestClient restClientLong; // timeout largo para respuestas grandes (órganos L)

    @PostConstruct
    void init() {
        this.restClient = RestClient.builder()
                .requestFactory(sslPermissive(connectMs, readMs))
                .defaultHeader("Accept", "application/json")
                .build();
        this.restClientLong = RestClient.builder()
                .requestFactory(sslPermissive(connectMs, 120_000))
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** Fetches flat catalog: [{id, descripcion}] */
    public List<CatItem> fetchPlano(String path, String... extraParams) {
        StringBuilder url = new StringBuilder(BASE).append(path).append("?vpd=GE");
        for (int i = 0; i + 1 < extraParams.length; i += 2) {
            url.append("&").append(extraParams[i]).append("=").append(extraParams[i + 1]);
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = restClient.get().uri(url.toString()).retrieve().body(List.class);
            if (items == null) return List.of();
            return items.stream()
                    .filter(m -> m.get("id") != null && m.get("descripcion") != null)
                    .map(m -> new CatItem(((Number) m.get("id")).intValue(), m.get("descripcion").toString()))
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching catalog {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    /** Fetches flat catalog with retry on 429/timeout */
    public List<CatItem> fetchPlanoConRetry(String path, String... extraParams) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            List<CatItem> result = fetchPlano(path, extraParams);
            if (!result.isEmpty()) return result;
            if (attempt < MAX_RETRIES) {
                long wait = RETRY_BASE_MS * attempt;
                log.warn("Catálogo {} vacío en intento {}/{}, reintentando en {}ms", path, attempt, MAX_RETRIES, wait);
                sleep(wait);
            }
        }
        return List.of();
    }

    /** Fetches tree catalog (like organos): flattens children recursively. Uses long timeout + retry. */
    @SuppressWarnings("unchecked")
    public List<OrganoItem> fetchOrganos(String tipoAdmin) {
        String url = BASE + "/organos?vpd=GE&idAdmon=" + tipoAdmin;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<Map<String, Object>> raices = restClientLong.get().uri(url).retrieve().body(List.class);
                if (raices == null) return List.of();
                List<OrganoItem> result = new ArrayList<>();
                for (Object r : raices) {
                    if (r instanceof Map<?, ?> m) aplanarOrgano((Map<String, Object>) m, null, tipoAdmin, result);
                }
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                log.warn("Error fetching organos tipoAdmin={} intento {}/{}: {}", tipoAdmin, attempt, MAX_RETRIES, e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                long wait = RETRY_BASE_MS * attempt;
                log.info("Reintentando organos tipoAdmin={} en {}ms", tipoAdmin, wait);
                sleep(wait);
            }
        }
        log.error("Organos tipoAdmin={}: agotados {} intentos", tipoAdmin, MAX_RETRIES);
        return List.of();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @SuppressWarnings("unchecked")
    private void aplanarOrgano(Map<String, Object> nodo, Integer parentId, String tipoAdmin, List<OrganoItem> acc) {
        Object idObj = nodo.get("id");
        Object descObj = nodo.get("descripcion");
        if (idObj == null || descObj == null) return;
        int id = ((Number) idObj).intValue();
        acc.add(new OrganoItem(id, descObj.toString(), parentId, tipoAdmin));
        Object children = nodo.get("children");
        if (children instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof Map<?, ?> m) aplanarOrgano((Map<String, Object>) m, id, tipoAdmin, acc);
            }
        }
    }

    /**
     * Searches convocatorias for a catalog filter, paginated.
     * Returns list of numeroConvocatoria strings found on that page.
     * Returns empty list if no more results or error.
     */
    public PaginaIndice buscarPorFiltro(String paramNombre, String paramValor, int pagina) {
        String url = BASE + "/convocatorias/busqueda?vpd=GE&pageSize=50&page=" + pagina
                + "&" + paramNombre + "=" + paramValor;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.get().uri(url).retrieve().body(Map.class);
            if (resp == null) return new PaginaIndice(List.of(), true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) resp.get("content");
            if (content == null || content.isEmpty()) return new PaginaIndice(List.of(), true);
            List<String> numeros = content.stream()
                    .map(m -> m.get("numeroConvocatoria"))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .toList();
            boolean esUltima = Boolean.TRUE.equals(resp.get("last"));
            return new PaginaIndice(numeros, esUltima);
        } catch (Exception e) {
            log.warn("Error buscando índice param={}={} pag={}: {}", paramNombre, paramValor, pagina, e.getMessage());
            return new PaginaIndice(List.of(), true);
        }
    }

    public record CatItem(int id, String descripcion) {}
    public record OrganoItem(int id, String descripcion, Integer parentId, String tipoAdmin) {}
    public record PaginaIndice(List<String> numerosConvocatoria, boolean esUltima) {}

    private SimpleClientHttpRequestFactory sslPermissive(int connectMs, int readMs) {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String t) {}
                public void checkServerTrusted(X509Certificate[] c, String t) {}
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory() {
                @Override protected void prepareConnection(HttpURLConnection con, String method) throws java.io.IOException {
                    if (con instanceof javax.net.ssl.HttpsURLConnection https) {
                        https.setSSLSocketFactory(ctx.getSocketFactory());
                        https.setHostnameVerifier((h, s) -> true);
                    }
                    super.prepareConnection(con, method);
                }
            };
            f.setConnectTimeout(connectMs);
            f.setReadTimeout(readMs);
            return f;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear SSL context permisivo", e);
        }
    }
}
