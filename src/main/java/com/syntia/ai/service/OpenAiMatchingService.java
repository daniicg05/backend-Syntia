package com.syntia.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OpenAiMatchingService {
    private static final String SYSTEM_PROMPT = """
            Eres el motor de recomendaciones de Syntia, plataforma española de ayudas y subvenciones públicas. \
            Se te proporciona: el perfil y proyecto de un usuario, y los datos de una convocatoria real de la BDNS. \
            Cuando se incluya la sección '=== CONTENIDO OFICIAL DE LA CONVOCATORIA ===', \
            DEBES usarla como fuente primaria para determinar requisitos, beneficiarios y procedimiento. \
            Si no hay contenido oficial, infiere a partir del título y organismo.
            
            TU TAREA tiene dos partes:
            
            PARTE 1 - MATCHING (puntuación 0-100):
            - 90-100: el perfil/proyecto cumple claramente los requisitos de la convocatoria.
            - 70-89: alta compatibilidad con algún matiz a verificar.
            - 50-69: compatible pero hay diferencias relevantes.
            - 30-49: compatibilidad baja.
            - 0-29: incompatible.
            La 'explicacion': máximo 2 frases. Primera: punto fuerte de compatibilidad. \
            Segunda: requisito específico de la convocatoria que el usuario debe verificar.
            
            PARTE 2 - GUÍA DE SOLICITUD (8 pasos separados por |):
            PASO 1: REQUISITOS LEGALES — Incluye SIEMPRE estos requisitos universales (Ley 38/2003 art. 13): \
            estar al corriente de obligaciones tributarias (AEAT), al corriente con la Seguridad Social (TGSS), \
            no incurso en prohibiciones del art. 13 LGS. Luego añade los requisitos ESPECÍFICOS de la convocatoria \
            (tipo de beneficiario, sector, tamaño empresa, antigüedad, etc.).
            PASO 2: DOCUMENTACIÓN OBLIGATORIA — Lista concreta: certificado AEAT, certificado TGSS, \
            declaración responsable art. 13, NIF/CIF, y documentos específicos de esta convocatoria \
            (memoria técnica, presupuesto desglosado, plan de empresa, etc.).
            PASO 3: ACCESO Y PRESENTACIÓN — Indica la sede electrónica ESPECÍFICA del organismo convocante. \
            Menciona los medios de identificación aceptados (certificado digital, Cl@ve, DNIe). \
            Indica si se necesita AutoFirma para la firma electrónica.
            PASO 4: PLAZOS Y CALENDARIO — Fecha de cierre si la conoces, si cuenta en días hábiles o naturales, \
            plazo de resolución previsto, fecha mínima de inicio de actividad subvencionable.
            PASO 5: RÉGIMEN DE CONCESIÓN — Indica si es concurrencia competitiva (se comparan solicitudes), \
            concesión directa o por orden de presentación. Si es competitiva, menciona criterios de valoración principales.
            PASO 6: TRAS LA CONCESIÓN — Obligaciones del beneficiario: plazo de aceptación, \
            inicio de la actividad, comunicación de incidencias, compatibilidad con otras ayudas (minimis).
            PASO 7: JUSTIFICACIÓN — Tipo de cuenta justificativa (simplificada u ordinaria), \
            plazo para justificar, documentos necesarios (facturas, memoria final, indicadores).
            PASO 8: ADVERTENCIAS CRÍTICAS — Errores frecuentes de exclusión, incompatibilidades, \
            diferencia entre el extracto (BOE/boletín) y las bases reguladoras (texto íntegro con requisitos completos). \
            Recuerda al usuario que debe leer las bases reguladoras completas, no solo el extracto.
            
            Si no tienes contenido oficial suficiente para algún paso, indica 'Consultar las bases reguladoras \
            de la convocatoria para requisitos exactos' en lugar de inventar información.
            
            RESPONDE ÚNICAMENTE con este JSON (sin texto adicional fuera del JSON):
            {"puntuacion": N, "explicacion": "texto", "sector": "UNA_PALABRA", \
            "guia": "PASO 1: texto|PASO 2: texto|PASO 3: texto|PASO 4: texto|PASO 5: texto|PASO 6: texto|PASO 7: texto|PASO 8: texto"}
            """;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public OpenAiMatchingService(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = new ObjectMapper();
    }

    public ResultadoIA analizar(Proyecto proyecto, Perfil perfil, Convocatoria convocatoria, String detalleTexto) {
        String userPrompt = construirPrompt(proyecto, perfil, convocatoria, detalleTexto);
        log.debug("Prompt OpenAI proy={} conv={} detalle={}chars",
                proyecto.getId(), convocatoria.getId(),
                detalleTexto != null ? detalleTexto.length() : 0);
        String respuesta = openAiClient.chat(SYSTEM_PROMPT, userPrompt);
        return parsearRespuesta(respuesta, proyecto, convocatoria);
    }

    /**
     * Analiza una convocatoria leyendo sus datos y el detalle BDNS.
     * Extrae requisitos, documentación y plazos sin contexto de usuario.
     */
    public ResultadoIA analizarConvocatoria(Convocatoria convocatoria, String detalleTexto) {
        String userPrompt = construirPromptSoloConvocatoria(convocatoria, detalleTexto);
        log.debug("Prompt OpenAI (convocatoria) conv={} detalle={}chars",
                convocatoria.getId(), detalleTexto != null ? detalleTexto.length() : 0);
        String respuesta = openAiClient.chat(SYSTEM_PROMPT, userPrompt);
        return parsearRespuestaSinProyecto(respuesta, convocatoria);
    }

    private String construirPrompt(Proyecto proyecto, Perfil perfil, Convocatoria convocatoria, String detalleTexto) {
        StringBuilder sb = new StringBuilder();

        // ── CONVOCATORIA (primero para que la IA la tenga clara antes de leer el perfil) ──
        sb.append("=== CONVOCATORIA PÚBLICA (BDNS) ===\n");
        sb.append("Título: ").append(convocatoria.getTitulo()).append("\n");
        appendIfPresent(sb, "Organismo", convocatoria.getFuente());
        appendIfPresent(sb, "Tipo", convocatoria.getTipo());
        appendIfPresent(sb, "Ámbito", convocatoria.getUbicacion());
        appendIfPresent(sb, "Sector", convocatoria.getSector());
        if (convocatoria.getFechaCierre() != null)
            sb.append("Cierre: ").append(convocatoria.getFechaCierre()).append("\n");
        // URL excluida del prompt — no aporta valor semántico y gasta tokens

        // ── CONTENIDO REAL DE LA CONVOCATORIA (obtenido de la API BDNS) ──
        if (detalleTexto != null && !detalleTexto.isBlank()) {
            sb.append("\n=== CONTENIDO OFICIAL DE LA CONVOCATORIA ===\n");
            String detalleLimpio = limpiarTexto(detalleTexto);
            sb.append(detalleLimpio).append("\n");
            sb.append("(Usa este contenido para requisitos EXACTOS y guía precisa.)\n");
        } else {
            sb.append("\n(Detalle no disponible. Infiere a partir del título y organismo.)\n");
        }

        // ── PROYECTO DEL USUARIO ──
        sb.append("\n=== PROYECTO ===\n");
        appendIfPresent(sb, "Nombre", proyecto.getNombre());
        appendIfPresent(sb, "Sector", proyecto.getSector());
        appendIfPresent(sb, "Ubicación", proyecto.getUbicacion());
        appendIfPresent(sb, "Descripción", proyecto.getDescripcion());

        // ── PERFIL DE LA ENTIDAD (solo campos con valor, sin duplicar sector si coincide con proyecto) ──
        if (perfil != null) {
            sb.append("\n=== PERFIL ENTIDAD ===\n");
            appendIfPresent(sb, "Tipo entidad", perfil.getTipoEntidad());
            // Solo incluir sector del perfil si es diferente al del proyecto
            if (perfil.getSector() != null && !perfil.getSector().isBlank()
                    && !perfil.getSector().equalsIgnoreCase(proyecto.getSector())) {
                sb.append("Sector entidad: ").append(perfil.getSector()).append("\n");
            }
            // Solo incluir ubicación del perfil si es diferente a la del proyecto
            if (perfil.getUbicacion() != null && !perfil.getUbicacion().isBlank()
                    && !perfil.getUbicacion().equalsIgnoreCase(proyecto.getUbicacion())) {
                sb.append("Ubicación entidad: ").append(perfil.getUbicacion()).append("\n");
            }
            appendIfPresent(sb, "Objetivos", perfil.getObjetivos());
            appendIfPresent(sb, "Necesidades financiación", perfil.getNecesidadesFinanciacion());
            appendIfPresent(sb, "Descripción libre", perfil.getDescripcionLibre());
        }

        sb.append("\n=== INSTRUCCIÓN ===\n");
        if (detalleTexto != null) {
            sb.append("Usa el CONTENIDO OFICIAL para requisitos y guía EXACTOS.");
        } else {
            sb.append("No hay contenido oficial. Infiere requisitos y guía del título y organismo.");
        }

        return sb.toString();
    }

    /**
     * Solo añade al StringBuilder si el valor no está vacío. Ahorra tokens eliminando "No indicado".
     */
    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    /**
     * Máximo de caracteres del detalle BDNS que se envía al prompt de evaluación.
     */
    private static final int MAX_DETALLE_CHARS = 1500;

    /**
     * Limpiar texto de BDNS: elimina HTML, normaliza espacios y trunca a {@link #MAX_DETALLE_CHARS}.
     */
    private String limpiarTexto(String texto) {
        if (texto == null) return "";
        String limpio = texto.replaceAll("<[^>]+>", " ");
        limpio = limpio.replaceAll("\\s+", " ").trim();
        if (limpio.length() > MAX_DETALLE_CHARS) {
            limpio = limpio.substring(0, MAX_DETALLE_CHARS) + "...";
        }
        return limpio;
    }

    private ResultadoIA parsearRespuesta(String respuesta, Proyecto proyecto, Convocatoria convocatoria) {
        try {
            JsonNode node = objectMapper.readTree(respuesta);
            int puntuacion = Math.max(0, Math.min(100, node.path("puntuacion").asInt()));
            String explicacion = node.path("explicacion").asText("Sin explicación disponible.");
            String sector = node.path("sector").asText(null);
            if (sector != null && sector.isBlank()) sector = null;
            String guia = node.path("guia").asText(null);
            if (guia != null && guia.isBlank()) guia = null;
            log.debug("OpenAI punt={} sector='{}' proy={} conv={}", puntuacion, sector, proyecto.getId(), convocatoria.getId());
            return new ResultadoIA(puntuacion, explicacion, sector, guia, true);
        } catch (Exception e) {
            log.warn("Error parseando OpenAI: {}. Raw={}", e.getMessage(), respuesta);
            throw new OpenAiClient.OpenAiUnavailableException("Respuesta no parseable: " + e.getMessage());
        }
    }


    private String construirPromptSoloConvocatoria(Convocatoria convocatoria, String detalleTexto) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== CONVOCATORIA PÚBLICA (BDNS) ===\n");
        sb.append("Título: ").append(convocatoria.getTitulo()).append("\n");
        appendIfPresent(sb, "Organismo", convocatoria.getFuente());
        appendIfPresent(sb, "Tipo", convocatoria.getTipo());
        appendIfPresent(sb, "Ámbito", convocatoria.getUbicacion());
        appendIfPresent(sb, "Sector", convocatoria.getSector());
        if (convocatoria.getFechaCierre() != null)
            sb.append("Cierre: ").append(convocatoria.getFechaCierre()).append("\n");

        if (detalleTexto != null && !detalleTexto.isBlank()) {
            sb.append("\n=== CONTENIDO OFICIAL DE LA CONVOCATORIA ===\n");
            sb.append(limpiarTexto(detalleTexto)).append("\n");
            sb.append("(Usa este contenido para requisitos EXACTOS y guía precisa.)\n");
        } else {
            sb.append("\n(Detalle no disponible. Infiere a partir del título y organismo.)\n");
        }

        sb.append("\n=== INSTRUCCIÓN ===\n");
        sb.append("Analiza esta convocatoria y extrae los requisitos, documentación y procedimiento. ");
        if (detalleTexto != null) {
            sb.append("Usa el CONTENIDO OFICIAL para requisitos y guía EXACTOS.");
        } else {
            sb.append("No hay contenido oficial. Infiere requisitos y guía del título y organismo.");
        }

        return sb.toString();
    }

    private ResultadoIA parsearRespuestaSinProyecto(String respuesta, Convocatoria convocatoria) {
        try {
            JsonNode node = objectMapper.readTree(respuesta);
            int puntuacion = Math.max(0, Math.min(100, node.path("puntuacion").asInt()));
            String explicacion = node.path("explicacion").asText("Sin explicación disponible.");
            String sector = node.path("sector").asText(null);
            if (sector != null && sector.isBlank()) sector = null;
            String guia = node.path("guia").asText(null);
            if (guia != null && guia.isBlank()) guia = null;
            log.debug("OpenAI (perfil) punt={} sector='{}' conv={}", puntuacion, sector, convocatoria.getId());
            return new ResultadoIA(puntuacion, explicacion, sector, guia, true);
        } catch (Exception e) {
            log.warn("Error parseando OpenAI: {}. Raw={}", e.getMessage(), respuesta);
            throw new OpenAiClient.OpenAiUnavailableException("Respuesta no parseable: " + e.getMessage());
        }
    }

    public record ResultadoIA(int puntuacion, String explicacion, String sector, String guia, boolean usadaIA) {
    }
}
