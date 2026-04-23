package com.syntia.ai.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.repository.RecomendacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de generación de guías de solicitud de subvenciones enriquecidas.
 * <p>
 * Usa OpenAI con un system prompt especializado en procedimientos administrativos
 * de subvenciones públicas españolas (LGS 38/2003, RD 887/2006, Ley 39/2015 LPACAP).
 * <p>
 * La guía devuelta sigue un esquema JSON completo con: resumen, métodos de solicitud,
 * documentos, requisitos universales, workflows detallados paso a paso,
 * guías visuales con prompts para galería Lightbox2 y disclaimer legal.
 * <p>
 * Este prompt se usa exclusivamente como {@code role: system}. El {@code role: user}
 * aporta los datos variables de cada convocatoria (perfil, proyecto, detalle BDNS, URL).
 */
@Slf4j
@Service
public class OpenAiGuiaService {

    /**
     * System prompt completo para generación de guías de solicitud de subvenciones.
     * Implementa 7 fases internas de procesamiento antes de devolver el JSON.
     * <p>
     * Referencia normativa: Ley 38/2003 General de Subvenciones art. 13,
     * RD 887/2006, Ley 39/2015 LPACAP art. 14.2.
     */
    private static final String GUIA_SYSTEM_PROMPT = """
            Eres un experto en procedimientos administrativos de subvenciones públicas en España \
            (Ley 38/2003 General de Subvenciones, RD 887/2006, Ley 39/2015 LPACAP).

            Tu tarea es generar una guía estructurada y completa para que un ciudadano pueda solicitar \
            la subvención indicada. La guía se mostrará como una galería visual interactiva paso a paso \
            en una aplicación web.

            EJECUTA INTERNAMENTE ESTAS FASES ANTES DE RESPONDER. NO muestres las fases. Devuelve SOLO el JSON final.

            FASE 1 — EXTRACCIÓN DE DATOS ÚTILES
            Extrae de la convocatoria SOLO la información práctica:
            - Título, organismo convocante, objetivo, quién puede solicitarla
            - Plazo de presentación (fecha límite real, no la de publicación en BOE)
            - Métodos de solicitud detectados: online, presencial, representante
            - Documentos requeridos (lista concreta, no genérica)
            - Enlace oficial a la sede electrónica o al trámite (si está disponible)
            - Bases reguladoras (referencia normativa)
            Ignora el texto legal y burocrático que no sea útil para el solicitante.

            FASE 2 — DETECCIÓN DE MÉTODOS DE SOLICITUD
            Detecta TODOS los canales disponibles para presentar la solicitud:
            - online: sede electrónica, plataforma digital, formulario web
            - presencial: registro administrativo, oficina del organismo
            - representante: mediante gestor, asesor o apoderado
            Si la convocatoria no especifica, infiere el método habitual del tipo de organismo \
            (ministerios y CCAA aplican tramitación electrónica obligatoria desde Ley 39/2015 art. 14.2 \
            para profesionales y personas jurídicas).

            FASE 3 — RECONSTRUCCIÓN DEL WORKFLOW COMPLETO
            Para el método de solicitud PRINCIPAL (normalmente online), reconstruye el flujo completo.
            Si la convocatoria no describe explícitamente todos los pasos, infiere los estándar \
            del procedimiento administrativo español (LGS + LPACAP).
            Genera UN SOLO workflow con máximo 8 pasos. Prioriza los más críticos.

            Para cada paso del workflow:
            - user_action: DEBE ser una instrucción precisa de navegación, indicando exactamente qué botón/enlace pulsar. \
            Ejemplo BUENO: "Pulsa 'Certificados' en el menú superior, luego selecciona 'Estar al corriente de obligaciones tributarias'" \
            Ejemplo MALO: "Obtener certificado tributario" (demasiado genérico)
            - portal_action: qué mostrará el sistema tras la acción del usuario
            - official_link: URL REAL del portal. OBLIGATORIO en TODOS los pasos. \
            Cada paso debe tener un official_link apuntando al portal o sección concreta que el usuario visitará.

            FASE 4 — GENERACIÓN DE GUÍA VISUAL CON URLS REALES
            Para CADA step del workflow genera los campos visuales:
            - title: título breve del paso (max 60 chars)
            - description: qué hace el usuario (max 150 chars)
            - screen_hint: la URL REAL exacta que el usuario verá en la barra del navegador en ese paso. \
            Ejemplos reales: "https://sede.agenciatributaria.gob.es/", \
            "https://clave.gob.es/clave_Home/registro.html", \
            "https://firmaelectronica.gob.es/Home/Descargas.html", \
            "https://sede.seg-social.gob.es/". \
            NUNCA inventes URLs. Usa solo URLs de portales gubernamentales españoles que existan realmente.
            - image_prompt: descripción breve de lo que se ve en esa pantalla del portal (max 150 chars)
            - official_link: la misma URL real del portal que el usuario debe visitar. OBLIGATORIO en todos los pasos. \
            Debe ser una URL funcional que abra el portal real en el navegador.

            URLS REALES CONOCIDAS que debes usar según el paso:
            - Certificado AEAT: https://sede.agenciatributaria.gob.es/
            - Certificado TGSS: https://sede.seg-social.gob.es/
            - Cl@ve PIN/Permanente: https://clave.gob.es/clave_Home/registro.html
            - Certificado digital FNMT: https://www.sede.fnmt.gob.es/certificados
            - AutoFirma descarga: https://firmaelectronica.gob.es/Home/Descargas.html
            - Registro electrónico general: https://rec.redsara.es/registro/action/are/acceso.do
            - Portal BDNS: https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias
            - Si la convocatoria es de un ministerio, usa la sede del ministerio real.
            - Si es autonómica, usa la sede electrónica de la CCAA correspondiente.

            Genera UN SOLO visual_guide correspondiente al workflow principal.

            FASE 5 — CHECKLIST UNIVERSAL (LGS art. 13)
            SIEMPRE incluye estos requisitos universales en el PASO 1, independientemente de lo que diga la convocatoria:
            - Estar al corriente de obligaciones tributarias (AEAT) — certificado positivo
            - Estar al corriente con la Seguridad Social (TGSS) — certificado de situación de cotización
            - No estar incurso en prohibiciones del art. 13.2 LGS — declaración responsable
            - No tener deudas de reintegro de subvenciones anteriores
            - Declaración de otras ayudas recibidas en régimen de minimis (si aplica)
            Añade a continuación los requisitos ESPECÍFICOS de la convocatoria.

            FASE 6 — ADVERTENCIAS LEGALES (SIEMPRE INCLUIR)
            El último campo del JSON SIEMPRE debe incluir una advertencia con:
            - Que esta guía tiene carácter orientativo
            - Que el usuario DEBE verificar los requisitos en la fuente oficial antes de presentar
            - Que la normativa aplicable es la LGS 38/2003 y el texto íntegro de las bases reguladoras
            - Que Syntia no asume responsabilidad por omisiones o interpretaciones

            FASE 7 — VALIDACIÓN DE JSON
            Antes de responder, verifica internamente:
            1. El JSON sigue exactamente el esquema indicado
            2. Todos los arrays son arrays JSON válidos
            3. No hay trailing commas
            4. Todas las claves requeridas existen
            5. Ningún campo supera su límite de caracteres
            6. Si detectas un error, corrígelo antes de devolver la respuesta

            ESQUEMA JSON DE RESPUESTA — Devuelve ÚNICAMENTE este JSON. Sin explicaciones, sin markdown. \
            Si un valor es desconocido usa null. Sé conciso en cada campo.

            {"grant_summary":{"title":"...","organism":"...","objective":"...",\
            "who_can_apply":"...","deadline":"DD/MM/YYYY o descripción",\
            "official_link":"URL o null","legal_basis":"..."},\
            "application_methods":[{"method":"online","description":"...","official_portal":"URL o null"}],\
            "required_documents":["doc1","doc2"],\
            "universal_requirements_lgs_art13":[\
            "Certificado al corriente con AEAT",\
            "Certificado situación cotización TGSS",\
            "Declaración responsable art. 13.2 LGS",\
            "Sin deudas de reintegro de subvenciones",\
            "Declaración ayudas minimis (si aplica)"],\
            "workflows":[{"method":"online",\
            "steps":[{"step":1,"phase":"preparation",\
            "title":"...","description":"...",\
            "user_action":"...","portal_action":"...",\
            "required_documents":["..."],"official_link":null,\
            "estimated_time_minutes":5}]}],\
            "visual_guides":[{"method":"online",\
            "steps":[{"step":1,"phase":"preparation",\
            "title":"...","description":"...",\
            "screen_hint":"https://url-real-del-portal.gob.es/pagina",\
            "image_prompt":"...",\
            "official_link":"https://url-real-del-portal.gob.es/pagina"}]}],\
            "legal_disclaimer":"Guía orientativa generada con datos de BDNS. Verificar requisitos en convocatoria oficial y bases reguladoras. Normativa: Ley 38/2003 y RD 887/2006. Syntia no asume responsabilidad."}
            """;

    /** Máximo de caracteres del detalle BDNS que se envía al prompt de guía. */
    private static final int MAX_DETALLE_CHARS = 3000;

    /** Versión del prompt — incrementar al cambiar el formato para invalidar guías cacheadas. */
    private static final int PROMPT_VERSION = 4;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final RecomendacionRepository recomendacionRepository;

    public OpenAiGuiaService(OpenAiClient openAiClient, RecomendacionRepository recomendacionRepository) {
        this.openAiClient = openAiClient;
        this.objectMapper = new ObjectMapper();
        this.recomendacionRepository = recomendacionRepository;
    }

    /**
     * Invalida guías enriquecidas cacheadas del formato anterior (sin URLs reales).
     * Se ejecuta una sola vez cuando la aplicación está completamente arrancada.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void invalidarGuiasCacheadasAntiguas() {
        int invalidadas = recomendacionRepository.invalidarTodasLasGuiasEnriquecidas();
        if (invalidadas > 0) {
            log.info("Guías enriquecidas invalidadas al arrancar (prompt v{}): {}", PROMPT_VERSION, invalidadas);
        }
    }

    /**
     * Genera una guía de solicitud completa para una convocatoria de subvención.
     * <p>
     * El system prompt implementa las 7 fases del procedimiento administrativo español
     * y devuelve un JSON estructurado listo para mostrar como galería visual interactiva.
     *
     * @param proyecto      proyecto del usuario que solicita la subvención
     * @param perfil        perfil de la entidad solicitante (puede ser null)
     * @param convocatoria  convocatoria de la BDNS
     * @param detalleTexto  contenido oficial de la convocatoria (obtenido de la API BDNS)
     * @param urlOficial    URL oficial de la convocatoria en el portal BDNS (puede ser null)
     * @return GuiaSubvencionDTO con la guía completa parseada
     * @throws OpenAiClient.OpenAiUnavailableException si OpenAI no está disponible
     */
    public GuiaSubvencionDTO generarGuia(Proyecto proyecto, Perfil perfil,
                                         Convocatoria convocatoria, String detalleTexto,
                                         String urlOficial) {
        return generarGuiaInterna(proyecto, perfil, convocatoria, detalleTexto, urlOficial);
    }

    /**
     * Genera guía directamente para una convocatoria, sin proyecto obligatorio.
     */
    public GuiaSubvencionDTO generarGuiaSinProyecto(Perfil perfil,
                                                     Convocatoria convocatoria, String detalleTexto,
                                                     String urlOficial) {
        return generarGuiaInterna(null, perfil, convocatoria, detalleTexto, urlOficial);
    }

    private GuiaSubvencionDTO generarGuiaInterna(Proyecto proyecto, Perfil perfil,
                                                  Convocatoria convocatoria, String detalleTexto,
                                                  String urlOficial) {
        String userPrompt = construirUserPrompt(proyecto, perfil, convocatoria, detalleTexto, urlOficial);
        log.info("Generando guía enriquecida para convocatoria='{}' proyecto={}",
                convocatoria.getTitulo(), proyecto.getId());

        String respuesta = openAiClient.chatLarge(GUIA_SYSTEM_PROMPT, userPrompt);
        return parsearGuia(respuesta, convocatoria);
    }

    /**
     * Construye el user prompt con los datos variables de la convocatoria y el solicitante.
     * Se envía como {@code role: user} — nunca se mezcla con el system prompt.
     */
    private String construirUserPrompt(Proyecto proyecto, Perfil perfil,
                                       Convocatoria convocatoria, String detalleTexto,
                                       String urlOficial) {
        StringBuilder sb = new StringBuilder();

        // ── CONVOCATORIA ──
        sb.append("=== CONVOCATORIA PÚBLICA (BDNS) ===\n");
        sb.append("Título: ").append(convocatoria.getTitulo()).append("\n");
        appendIfPresent(sb, "Organismo", convocatoria.getFuente());
        appendIfPresent(sb, "Tipo", convocatoria.getTipo());
        appendIfPresent(sb, "Ámbito geográfico", convocatoria.getUbicacion());
        appendIfPresent(sb, "Sector", convocatoria.getSector());
        if (convocatoria.getFechaCierre() != null) {
            sb.append("Fecha de cierre: ").append(convocatoria.getFechaCierre()).append("\n");
        }
        if (urlOficial != null && !urlOficial.isBlank()) {
            sb.append("URL oficial: ").append(urlOficial).append("\n");
        }

        // ── DETALLE OFICIAL BDNS ──
        if (detalleTexto != null && !detalleTexto.isBlank()) {
            sb.append("\n=== CONTENIDO OFICIAL DE LA CONVOCATORIA ===\n");
            String detalleLimpio = limpiarTexto(detalleTexto);
            sb.append(detalleLimpio).append("\n");
            sb.append("(Usa este contenido como fuente primaria para requisitos, documentación y procedimiento.)\n");
        } else {
            sb.append("\n(Detalle oficial no disponible. Infiere procedimiento estándar a partir del organismo y tipo.)\n");
        }

        // ── PERFIL DEL SOLICITANTE ──
        if (perfil != null) {
            sb.append("\n=== PERFIL DEL SOLICITANTE ===\n");
            appendIfPresent(sb, "Tipo de entidad", perfil.getTipoEntidad());
            appendIfPresent(sb, "Sector", perfil.getSector());
            appendIfPresent(sb, "Ubicación", perfil.getUbicacion());
            appendIfPresent(sb, "Objetivos", perfil.getObjetivos());
            appendIfPresent(sb, "Necesidades de financiación", perfil.getNecesidadesFinanciacion());
        }

        // ── PROYECTO ──
        if (proyecto != null) {
            sb.append("\n=== PROYECTO DEL SOLICITANTE ===\n");
            appendIfPresent(sb, "Nombre", proyecto.getNombre());
            appendIfPresent(sb, "Sector", proyecto.getSector());
            appendIfPresent(sb, "Ubicación", proyecto.getUbicacion());
            appendIfPresent(sb, "Descripción", proyecto.getDescripcion());
        }

        sb.append("\n=== INSTRUCCIÓN ===\n");
        sb.append("Genera la guía de solicitud completa en formato JSON siguiendo el esquema del system prompt. ");
        sb.append("Prioriza la información del CONTENIDO OFICIAL si está disponible.");

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private String limpiarTexto(String texto) {
        if (texto == null) return "";
        String limpio = texto.replaceAll("<[^>]+>", " ");
        limpio = limpio.replaceAll("\\s+", " ").trim();
        if (limpio.length() > MAX_DETALLE_CHARS) {
            limpio = limpio.substring(0, MAX_DETALLE_CHARS) + "...";
        }
        return limpio;
    }

    /**
     * Parsea la respuesta JSON de OpenAI a {@link GuiaSubvencionDTO}.
     *
     * @param respuesta JSON string devuelto por OpenAI
     * @param convocatoria convocatoria para logging
     * @return DTO parseado
     * @throws OpenAiClient.OpenAiUnavailableException si no se puede parsear
     */
    private GuiaSubvencionDTO parsearGuia(String respuesta, Convocatoria convocatoria) {
        try {
            GuiaSubvencionDTO guia = objectMapper.readValue(respuesta, GuiaSubvencionDTO.class);
            log.info("Guía enriquecida generada para '{}': {} métodos, {} workflows",
                    convocatoria.getTitulo(),
                    guia.getApplicationMethods() != null ? guia.getApplicationMethods().size() : 0,
                    guia.getWorkflows() != null ? guia.getWorkflows().size() : 0);
            return guia;
        } catch (Exception e) {
            log.warn("Error parseando guía enriquecida para '{}': {}. Raw={}",
                    convocatoria.getTitulo(), e.getMessage(),
                    respuesta != null && respuesta.length() > 200 ? respuesta.substring(0, 200) + "..." : respuesta);
            throw new OpenAiClient.OpenAiUnavailableException("Respuesta de guía no parseable: " + e.getMessage());
        }
    }

    /**
     * Serializa un DTO de guía a JSON string para persistencia.
     *
     * @param guia DTO de la guía
     * @return JSON string o null si falla
     */
    public String serializarGuia(GuiaSubvencionDTO guia) {
        try {
            return objectMapper.writeValueAsString(guia);
        } catch (Exception e) {
            log.warn("Error serializando guía: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deserializa un JSON string almacenado en BD a DTO.
     *
     * @param json JSON string de la guía
     * @return DTO parseado o null si falla
     */
    public GuiaSubvencionDTO deserializarGuia(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, GuiaSubvencionDTO.class);
        } catch (Exception e) {
            log.warn("Error deserializando guía almacenada: {}", e.getMessage());
            return null;
        }
    }
}