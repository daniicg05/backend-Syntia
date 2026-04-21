package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.repository.ConvocatoriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class AnalisisIaService {

    private static final String SYSTEM_PROMPT = """
        Eres un experto en subvenciones publicas espanolas (LGS 38/2003, RD 887/2006).
        Analiza la convocatoria y devuelve UNICAMENTE este JSON sin markdown:
        {
          "puntuacionCompatibilidad": <0-100>,
          "resumenEjecutivo": "...",
          "descripcionObjetivo": "...",
          "requisitosElegibilidad": "...",
          "cuantiaDetalle": "...",
          "plazoPresentacion": "...",
          "formaPresentacion": "...",
          "documentacionRequerida": "...",
          "procedimientoResolucion": "...",
          "criteriosValoracion": "...",
          "obligacionesBeneficiario": "...",
          "incompatibilidades": "...",
          "contactoGestion": "...",
          "advertenciasClave": "...",
          "sectorInferido": "..."
        }
        """;

    private final ConvocatoriaRepository convocatoriaRepository;
    private final BdnsClientService bdnsClientService;
    private final OpenAiClient openAiClient;

    public AnalisisIaService(ConvocatoriaRepository convocatoriaRepository,
                             BdnsClientService bdnsClientService,
                             OpenAiClient openAiClient) {
        this.convocatoriaRepository = convocatoriaRepository;
        this.bdnsClientService = bdnsClientService;
        this.openAiClient = openAiClient;
    }

    public void analizar(Long bdnsId, SseEmitter emitter) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                // Fase 1: buscar convocatoria
                emitter.send(SseEmitter.event().name("estado").data("\"Cargando convocatoria...\""));
                Convocatoria conv = convocatoriaRepository.findByIdBdns(String.valueOf(bdnsId))
                        .orElseThrow(() -> new RuntimeException("Convocatoria no encontrada para BDNS id: " + bdnsId));

                // Emitir detalle basico
                String detalleJson = buildDetalleJson(conv);
                emitter.send(SseEmitter.event().name("detalle").data(detalleJson));

                // Fase 2: analisis IA
                emitter.send(SseEmitter.event().name("estado").data("\"Analizando con IA...\""));
                emitter.send(SseEmitter.event().name("analisis").data("{}"));

                String detalleTexto = conv.getNumeroConvocatoria() != null
                        ? bdnsClientService.obtenerDetalleTexto(conv.getNumeroConvocatoria())
                        : null;

                String userPrompt = buildUserPrompt(conv, detalleTexto);
                String respuesta = openAiClient.chatLarge(SYSTEM_PROMPT, userPrompt);

                // Emitir resultado completo
                String completadoJson = mergeDetalleConAnalisis(detalleJson, respuesta);
                emitter.send(SseEmitter.event().name("completado").data(completadoJson));
                emitter.complete();

            } catch (Exception e) {
                log.error("Error en analisis IA para bdnsId={}: {}", bdnsId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("\"" + e.getMessage() + "\""));
                    emitter.complete();
                } catch (Exception ignored) {}
            } finally {
                executor.shutdown();
            }
        });
    }

    private String buildDetalleJson(Convocatoria c) {
        return String.format(
            "{\"idBdns\":%s,\"titulo\":%s,\"organoConvocante\":%s,\"tipoAdministracion\":null," +
            "\"sedeElectronica\":null,\"presupuestoTotal\":%s,\"instrumento\":null," +
            "\"tipoConvocatoria\":%s,\"fechaRegistro\":null,\"tipoBeneficiario\":null," +
            "\"sectorEconomico\":%s,\"regionImpacto\":%s,\"finalidad\":%s," +
            "\"mecanismoRecuperacion\":null,\"extractoEnDiarioOficial\":null," +
            "\"solicitudIndefinida\":null,\"fechaInicioSolicitud\":null," +
            "\"fechaFinSolicitud\":%s,\"reglamentoUE\":null,\"saNumber\":null," +
            "\"saNumberEnlaceUE\":null,\"cofinanciadoFondosUE\":null,\"sectorProductos\":null," +
            "\"objetivos\":null,\"basesReguladoras\":null,\"urlBasesReguladoras\":null," +
            "\"documentos\":[],\"extractos\":[],\"urlOficial\":%s," +
            "\"puntuacionCompatibilidad\":null,\"resumenEjecutivo\":null," +
            "\"descripcionObjetivo\":null,\"requisitosElegibilidad\":null," +
            "\"cuantiaDetalle\":null,\"plazoPresentacion\":null,\"formaPresentacion\":null," +
            "\"documentacionRequerida\":null,\"procedimientoResolucion\":null," +
            "\"criteriosValoracion\":null,\"obligacionesBeneficiario\":null," +
            "\"incompatibilidades\":null,\"contactoGestion\":null," +
            "\"advertenciasClave\":null,\"sectorInferido\":null," +
            "\"numeroConvocatoria\":%s,\"tituloCooficial\":null}",
            c.getIdBdns() != null ? c.getIdBdns() : "null",
            jsonStr(c.getTitulo()), jsonStr(c.getFuente()),
            c.getPresupuesto() != null ? c.getPresupuesto() : "null",
            jsonStr(c.getTipo()), jsonStr(c.getSector()), jsonStr(c.getUbicacion()),
            jsonStr(c.getFinalidad()),
            c.getFechaCierre() != null ? "\"" + c.getFechaCierre() + "\"" : "null",
            jsonStr(c.getUrlOficial()), jsonStr(c.getNumeroConvocatoria())
        );
    }

    private String buildUserPrompt(Convocatoria c, String detalleTexto) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CONVOCATORIA ===\n");
        sb.append("Titulo: ").append(c.getTitulo()).append("\n");
        if (c.getFuente() != null) sb.append("Organismo: ").append(c.getFuente()).append("\n");
        if (c.getSector() != null) sb.append("Sector: ").append(c.getSector()).append("\n");
        if (c.getUbicacion() != null) sb.append("Ambito: ").append(c.getUbicacion()).append("\n");
        if (c.getFechaCierre() != null) sb.append("Cierre: ").append(c.getFechaCierre()).append("\n");
        if (c.getUrlOficial() != null) sb.append("URL: ").append(c.getUrlOficial()).append("\n");
        if (detalleTexto != null && !detalleTexto.isBlank()) {
            sb.append("\n=== CONTENIDO OFICIAL ===\n").append(detalleTexto);
        }
        sb.append("\n\nDevuelve SOLO el JSON del analisis.");
        return sb.toString();
    }

    private String mergeDetalleConAnalisis(String detalleJson, String analisisJson) {
        // Elimina el ultimo } del detalle y el primer { del analisis para fusionarlos
        String base = detalleJson.trim();
        String extra = analisisJson.trim();
        if (base.endsWith("}") && extra.startsWith("{")) {
            return base.substring(0, base.length() - 1) + "," + extra.substring(1);
        }
        return analisisJson;
    }

    private String jsonStr(String v) {
        if (v == null) return "null";
        return "\"" + v.replace("\"", "\\\"").replace("\n", " ") + "\"";
    }
}

