package com.syntia.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntia.ai.model.dto.ConvocatoriaAnalisisIADTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio NUEVO — no modifica ningún servicio existente.
 *
 * Orquesta el análisis enriquecido de una convocatoria BDNS con IA:
 *   1. Consulta JSON raw de BDNS directamente (RestClient con SSL permisivo,
 *      igual que BdnsClientService) → emite evento "detalle"
 *   2. Genera texto plano del mismo JSON para el prompt → emite evento "estado"
 *   3. Llama a OpenAiClient.chatLarge() con análisis profundo → emite evento "analisis"
 *   4. Ensambla DTO completo enriquecido → emite evento "completado"
 *
 * Reutiliza sin modificar: OpenAiClient (chatLarge)
 * No toca: BdnsClientService, MotorMatchingService, OpenAiMatchingService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConvocatoriaAnalisisIAService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    private static final String BDNS_DETALLE_URL =
            "https://www.infosubvenciones.es/bdnstrans/api/convocatorias/";

    // RestClient con SSL permisivo (igual que BdnsClientService existente)
    private final RestClient bdnsRestClient = crearRestClientSslPermisivo();

    // ─────────────────────────────────────────────────────────────────────────
    // API PÚBLICA
    // ─────────────────────────────────────────────────────────────────────────

    public void generarAnalisisStream(Long idBdns, SseEmitter emitter) {
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        CompletableFuture.runAsync(() -> {
            try {

                // ── PASO 1: JSON raw de BDNS ──────────────────────────────
                enviarEvento(emitter, "estado", "Consultando convocatoria en BDNS...");
                Map<String, Object> raw = obtenerRawBdns(idBdns);

                // ── PASO 2: Mapear campos estructurales y emitir ──────────
                ConvocatoriaAnalisisIADTO dtoBase = mapearCamposBdns(raw, idBdns);
                enviarEvento(emitter, "detalle", dtoBase);

                // ── PASO 3: Preparar texto para IA ────────────────────────
                enviarEvento(emitter, "estado", "Preparando análisis con IA...");
                String textoDetalle = construirTextoDetalle(raw, dtoBase);

                // ── PASO 4: Llamada a OpenAI (chatLarge para análisis profundo)
                enviarEvento(emitter, "estado", "Analizando convocatoria con IA...");
                String respuestaIA = openAiClient.chatLarge(
                        buildSystemPrompt(),
                        buildUserPrompt(textoDetalle, dtoBase)
                );

                // ── PASO 5: Parsear respuesta IA ──────────────────────────
                Map<String, Object> camposIA = parsearRespuestaIA(respuestaIA);
                enviarEvento(emitter, "analisis", camposIA);

                // ── PASO 6: DTO completo enriquecido ─────────────────────
                ConvocatoriaAnalisisIADTO dtoCompleto = enriquecerConIA(dtoBase, camposIA);
                enviarEvento(emitter, "completado", dtoCompleto);

                emitter.complete();

            } catch (Exception e) {
                log.error("Error análisis IA convocatoria {}: {}", idBdns, e.getMessage());
                enviarEvento(emitter, "error", e.getMessage());
                emitter.complete();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTA BDNS RAW
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> obtenerRawBdns(Long idBdns) {
        try {
            String json = bdnsRestClient.get()
                    .uri(BDNS_DETALLE_URL + idBdns + "?vpd=GE&vln=es")
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) return Collections.emptyMap();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("No se pudo obtener raw BDNS para {}: {}", idBdns, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPEO BDNS → DTO  (campos exactos del DTO real)
    // ─────────────────────────────────────────────────────────────────────────

    private ConvocatoriaAnalisisIADTO mapearCamposBdns(Map<String, Object> raw, Long idBdns) {
        String numConv = str(raw, "numeroConvocatoria");
        return ConvocatoriaAnalisisIADTO.builder()
                // Bloque 1 — Identificación
                .idBdns(idBdns)
                .numeroConvocatoria(numConv)
                .titulo(str(raw, "descripcion", "tituloConvocatoria"))
                .tituloCooficial(str(raw, "descripcionCooficial"))
                // Bloque 2 — Órgano
                .organoConvocante(str(raw, "organoConvocante", "nombreOrgano"))
                .tipoAdministracion(str(raw, "tipoAdministracion", "nivel1"))
                .sedeElectronica(str(raw, "sedeElectronica", "urlSede"))
                // Bloque 3 — Económicos
                .presupuestoTotal(dbl(raw, "presupuestoTotal", "importeTotal", "dotacion"))
                .instrumento(str(raw, "instrumento", "tipoInstrumento"))
                .tipoConvocatoria(str(raw, "tipoConvocatoria"))
                .fechaRegistro(str(raw, "fechaRegistro", "fechaRecepcion"))
                // Bloque 4 — Ámbito
                .tipoBeneficiario(str(raw, "tipoBeneficiario", "tiposBeneficiarios"))
                .sectorEconomico(str(raw, "sectorEconomico", "sector"))
                .regionImpacto(str(raw, "nivel2", "region", "regionImpacto"))
                .finalidad(str(raw, "finalidad", "politicaGasto"))
                .mecanismoRecuperacion(bool(raw, "mecanismoRecuperacion"))
                // Bloque 5 — Solicitud
                .extractoEnDiarioOficial(bool(raw, "extractoDiarioOficial"))
                .solicitudIndefinida(bool(raw, "solicitudIndefinida"))
                .fechaInicioSolicitud(str(raw, "fechaInicioSolicitud", "fechaDesde"))
                .fechaFinSolicitud(str(raw, "fechaFinSolicitud", "fechaHasta", "fechaCierre"))
                // Bloque 6 — Ayudas de Estado
                .reglamentoUE(str(raw, "reglamentoUE", "reglamento"))
                .saNumber(str(raw, "saNumber", "numeroSA"))
                .saNumberEnlaceUE(str(raw, "saNumberEnlace"))
                .cofinanciadoFondosUE(bool(raw, "cofinanciadoFondosUE"))
                .sectorProductos(str(raw, "sectorProductos"))
                .objetivos(str(raw, "objetivos", "objeto"))
                // Bloque 7 — Bases reguladoras
                .basesReguladoras(str(raw, "basesReguladoras", "normativa"))
                .urlBasesReguladoras(str(raw, "urlBasesReguladoras", "urlNormativa"))
                // Bloque 8 — Documentos (campo real: urlDescarga)
                .documentos(mapDocumentos(raw))
                // Bloque 9 — Extractos (campo real: tituloAnuncio)
                .extractos(mapExtractos(raw))
                // URL oficial
                .urlOficial(numConv != null
                        ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                        : null)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENRIQUECIMIENTO CON IA  (NO usa .toBuilder() — builder nuevo con todos los campos)
    // ─────────────────────────────────────────────────────────────────────────

    private ConvocatoriaAnalisisIADTO enriquecerConIA(
            ConvocatoriaAnalisisIADTO base,
            Map<String, Object> ia) {

        // Como @Builder no tiene toBuilder=true, reconstruimos con todos los campos del base
        return ConvocatoriaAnalisisIADTO.builder()
                // ── Campos del base (bloques 1-9) ────────────────────────
                .idBdns(base.getIdBdns())
                .numeroConvocatoria(base.getNumeroConvocatoria())
                .titulo(base.getTitulo())
                .tituloCooficial(base.getTituloCooficial())
                .organoConvocante(base.getOrganoConvocante())
                .tipoAdministracion(base.getTipoAdministracion())
                .sedeElectronica(base.getSedeElectronica())
                .presupuestoTotal(base.getPresupuestoTotal())
                .instrumento(base.getInstrumento())
                .tipoConvocatoria(base.getTipoConvocatoria())
                .fechaRegistro(base.getFechaRegistro())
                .tipoBeneficiario(base.getTipoBeneficiario())
                .sectorEconomico(base.getSectorEconomico())
                .regionImpacto(base.getRegionImpacto())
                .finalidad(base.getFinalidad())
                .mecanismoRecuperacion(base.getMecanismoRecuperacion())
                .extractoEnDiarioOficial(base.getExtractoEnDiarioOficial())
                .solicitudIndefinida(base.getSolicitudIndefinida())
                .fechaInicioSolicitud(base.getFechaInicioSolicitud())
                .fechaFinSolicitud(base.getFechaFinSolicitud())
                .reglamentoUE(base.getReglamentoUE())
                .saNumber(base.getSaNumber())
                .saNumberEnlaceUE(base.getSaNumberEnlaceUE())
                .cofinanciadoFondosUE(base.getCofinanciadoFondosUE())
                .sectorProductos(base.getSectorProductos())
                .objetivos(base.getObjetivos())
                .basesReguladoras(base.getBasesReguladoras())
                .urlBasesReguladoras(base.getUrlBasesReguladoras())
                .documentos(base.getDocumentos())
                .extractos(base.getExtractos())
                .urlOficial(base.getUrlOficial())
                // ── Campos IA (bloque 10) ────────────────────────────────
                .puntuacionCompatibilidad(toInt(ia.get("puntuacion")))
                .resumenEjecutivo(strMap(ia, "resumen"))
                .descripcionObjetivo(strMap(ia, "objeto"))
                .requisitosElegibilidad(strMap(ia, "requisitos"))
                .cuantiaDetalle(strMap(ia, "cuantia"))
                .plazoPresentacion(strMap(ia, "plazo"))
                .formaPresentacion(strMap(ia, "formaPresentacion"))
                .documentacionRequerida(strMap(ia, "documentacion"))
                .procedimientoResolucion(strMap(ia, "procedimiento"))
                .criteriosValoracion(strMap(ia, "criterios"))
                .obligacionesBeneficiario(strMap(ia, "obligaciones"))
                .incompatibilidades(strMap(ia, "incompatibilidades"))
                .contactoGestion(strMap(ia, "contacto"))
                .advertenciasClave(strMap(ia, "advertencias"))
                .sectorInferido(strMap(ia, "sectorInferido"))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPEO DOCUMENTOS Y EXTRACTOS  (nombres de campo reales del DTO)
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ConvocatoriaAnalisisIADTO.DocumentoDTO> mapDocumentos(Map<String, Object> raw) {
        List<ConvocatoriaAnalisisIADTO.DocumentoDTO> result = new ArrayList<>();
        Object docs = raw.get("documentos");
        if (!(docs instanceof List<?> lista)) return result;
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> d = (Map<String, Object>) m;
            result.add(ConvocatoriaAnalisisIADTO.DocumentoDTO.builder()
                    .nombre(str(d, "nombre", "titulo", "descripcion"))
                    .urlDescarga(str(d, "urlDescarga", "url", "enlace"))   // campo real: urlDescarga
                    .fechaPublicacion(str(d, "fechaPublicacion"))
                    .fechaRegistro(str(d, "fechaRegistro"))
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ConvocatoriaAnalisisIADTO.ExtractoDTO> mapExtractos(Map<String, Object> raw) {
        List<ConvocatoriaAnalisisIADTO.ExtractoDTO> result = new ArrayList<>();
        Object exts = raw.get("extractos");
        if (!(exts instanceof List<?> lista)) return result;
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> e = (Map<String, Object>) m;
            result.add(ConvocatoriaAnalisisIADTO.ExtractoDTO.builder()
                    .diarioOficial(str(e, "diarioOficial", "boletin"))
                    .fechaPublicacion(str(e, "fechaPublicacion"))
                    .tituloAnuncio(str(e, "tituloAnuncio", "titulo"))      // campo real: tituloAnuncio
                    .tituloCooficial(str(e, "tituloCooficial"))
                    .url(str(e, "url", "enlace"))
                    .build());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEXTO PARA EL PROMPT IA (construido desde el raw, sin llamadas extra)
    // ─────────────────────────────────────────────────────────────────────────

    private String construirTextoDetalle(Map<String, Object> raw, ConvocatoriaAnalisisIADTO base) {
        StringBuilder sb = new StringBuilder();
        raw.forEach((k, v) -> {
            if (v != null && !v.toString().isBlank()
                    && !(v instanceof List) && !(v instanceof Map)) {
                sb.append(k).append(": ").append(v).append("\n");
            }
        });
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROMPTS IA
    // ─────────────────────────────────────────────────────────────────────────

    private String buildSystemPrompt() {
        return """
                Eres un experto en subvenciones públicas españolas.
                Analiza la convocatoria y devuelve ÚNICAMENTE un JSON válido con estos campos exactos:
                {
                  "puntuacion": número entero 0-100,
                  "resumen": "resumen ejecutivo en 2-3 frases",
                  "objeto": "descripción detallada del objeto de la convocatoria",
                  "requisitos": "requisitos de elegibilidad del beneficiario",
                  "cuantia": "importe máximo, mínimo y modalidad de la ayuda",
                  "plazo": "plazo de presentación de solicitudes",
                  "formaPresentacion": "cómo y dónde presentar la solicitud",
                  "documentacion": "documentación requerida para solicitar",
                  "procedimiento": "procedimiento y criterios de resolución",
                  "criterios": "criterios de valoración de las solicitudes",
                  "obligaciones": "obligaciones del beneficiario si obtiene la ayuda",
                  "incompatibilidades": "incompatibilidades con otras ayudas",
                  "contacto": "órgano de contacto y datos de gestión",
                  "advertencias": "aspectos críticos a tener en cuenta",
                  "sectorInferido": "sector económico principal inferido"
                }
                Responde solo con el JSON. Sin texto antes ni después. Sin bloques ```json```.
                """;
    }

    private String buildUserPrompt(String textoDetalle, ConvocatoriaAnalisisIADTO base) {
        return """
                Convocatoria: %s
                Código BDNS: %s
                Presupuesto total: %s €
                Tipo beneficiario: %s
                Sector económico: %s
                Región de impacto: %s
                Tipo de convocatoria: %s
                Instrumento: %s
                
                Datos completos de la convocatoria:
                %s
                """.formatted(
                nvl(base.getTitulo()),
                nvl(base.getIdBdns()),
                nvl(base.getPresupuestoTotal()),
                nvl(base.getTipoBeneficiario()),
                nvl(base.getSectorEconomico()),
                nvl(base.getRegionImpacto()),
                nvl(base.getTipoConvocatoria()),
                nvl(base.getInstrumento()),
                textoDetalle
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARSEO RESPUESTA IA
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsearRespuestaIA(String respuesta) {
        try {
            String json = respuesta.trim()
                    .replaceAll("^```(?:json)?\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("No se pudo parsear respuesta IA: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SSL PERMISIVO  (mismo patrón que BdnsClientService existente)
    // ─────────────────────────────────────────────────────────────────────────

    private static RestClient crearRestClientSslPermisivo() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] c, String a) {}
                        public void checkServerTrusted(X509Certificate[] c, String a) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(java.net.HttpURLConnection conn, String method)
                        throws java.io.IOException {
                    if (conn instanceof javax.net.ssl.HttpsURLConnection https) {
                        https.setSSLSocketFactory(sc.getSocketFactory());
                        https.setHostnameVerifier((h, s) -> true);
                    }
                    super.prepareConnection(conn, method);
                }
            };
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(30_000);

            return RestClient.builder().requestFactory(factory).build();
        } catch (Exception e) {
            log.error("No se pudo crear RestClient SSL permisivo: {}", e.getMessage());
            return RestClient.create();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SSE HELPER
    // ─────────────────────────────────────────────────────────────────────────

    private void enviarEvento(SseEmitter emitter, String tipo, Object datos) {
        try {
            emitter.send(SseEmitter.event().name(tipo).data(datos));
        } catch (Exception e) {
            log.debug("Error enviando SSE '{}': {}", tipo, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS NULL-SAFE
    // ─────────────────────────────────────────────────────────────────────────

    private String str(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isBlank()) return val.toString();
        }
        return null;
    }

    private String strMap(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Double dbl(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val == null) continue;
            try { return Double.parseDouble(val.toString()); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Boolean bool(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Boolean b) return b;
        return "SI".equalsIgnoreCase(val.toString()) || "true".equalsIgnoreCase(val.toString());
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private String nvl(Object val) {
        return val != null ? val.toString() : "No indicado";
    }
}