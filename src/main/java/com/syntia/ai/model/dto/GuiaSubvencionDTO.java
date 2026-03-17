package com.syntia.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO principal que representa una guía completa de subvención.
 *
 * <p>Objetivo:</p>
 * <ul>
 * <li>Modelar la estructura JSON recibida/enviada por API para una guía de subvenciones.</li>
 * <li>Servir como contrato de datos entre capa de integración/controlador y servicios.</li>
 * <li>Desacoplar el formato externo (JSON) del dominio interno de la aplicación.</li>
 * </ul>
 *
 * <p>Detalles técnicos:</p>
 * <ul>
 * <li>{@link JsonIgnoreProperties}(ignoreUnknown = true) permite ignorar campos JSON no mapeados,
 * evitando fallos si el proveedor agrega propiedades nuevas.</li>
 * <li>{@link JsonProperty} mapea nombres de JSON en snake\_case a atributos Java en camelCase.</li>
 * <li>Las anotaciones de Lombok reducen boilerplate: getters, setters, constructores y builder.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuiaSubvencionDTO {

    /**
     * Resumen general de la subvención:
     * título, organismo, objetivo, destinatarios, plazo y base legal.
     *
     * JSON: `grant_summary`
     */
    @JsonProperty("grant_summary")
    private GrantSummary grantSummary;

    /**
     * Métodos disponibles para presentar la solicitud
     * (por ejemplo: telemático, presencial).
     *
     * JSON: `application_methods`
     */
    @JsonProperty("application_methods")
    private List<ApplicationMethod> applicationMethods;

    /**
     * Lista de documentos requeridos de forma general
     * para tramitar la subvención.
     *
     * JSON: `required_documents`
     */
    @JsonProperty("required_documents")
    private List<String> requiredDocuments;

    /**
     * Requisitos universales asociados al art.13 de la LGS
     * (Ley General de Subvenciones).
     *
     * JSON: `universal_requirements_lgs_art13`
     */
    @JsonProperty("universal_requirements_lgs_art13")
    private List<String> universalRequirementsLgsArt13;

    /**
     * Flujos operativos por método de solicitud.
     * Cada flujo agrupa pasos secuenciales de tramitación.
     *
     * JSON: `workflows`
     */
    private List<Workflow> workflows;

    /**
     * Guías visuales por método para apoyar al usuario
     * en pantallas, navegación y acciones concretas.
     *
     * JSON: `visual_guides`
     */
    @JsonProperty("visual_guides")
    private List<VisualGuide> visualGuides;

    /**
     * Aviso legal o descargo de responsabilidad asociado al contenido.
     *
     * JSON: `legal_disclaimer`
     */
    @JsonProperty("legal_disclaimer")
    private String legalDisclaimer;

    // ── Clases internas ──
    /**
     * Resumen informativo de la convocatoria de subvención.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GrantSummary {

        private String title; /** Título oficial o nombre de la subvención. */
        private String organism; /** Organismo convocante o entidad responsable. */
        private String objective; /** Finalidad/objetivo de la ayuda. */

        /**
         * Perfil de beneficiarios aptos para solicitar la subvención.
         *
         * JSON: `who_can_apply`
         */
        @JsonProperty("who_can_apply")
        private String whoCanApply;

        private String deadline; /** Fecha límite o referencia temporal del plazo de solicitud. */

        /**
         * Enlace oficial de consulta o tramitación.
         *
         * JSON: `official_link`
         */
        @JsonProperty("official_link")
        private String officialLink;

        /**
         * Marco normativo o fundamento jurídico de la convocatoria.
         *
         * JSON: `legal_basis`
         */
        @JsonProperty("legal_basis")
        private String legalBasis;
    }

    /**
     * Método/canal de presentación de la solicitud.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicationMethod {
        private String method; /** Nombre del método (ej.: "online", "presencial"). */
        private String description; /** Descripción funcional del canal y sus condiciones de uso. */

        /**
         * Portal oficial para ese método, si aplica.
         *
         * JSON: `official_portal`
         */
        @JsonProperty("official_portal")
        private String officialPortal;
    }

    /**
     * Flujo de tramitación para un método concreto.
     * Se compone de una secuencia ordenada de pasos.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Workflow {
        private String method; /** Método al que pertenece este flujo. */
        private List<WorkflowStep> steps; /** Pasos detallados del flujo. */
    }

    /**
     * Paso individual dentro de un flujo de tramitación.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowStep {
        private int step; /** Orden secuencial del paso dentro del flujo. */
        private String phase; /** Fase funcional (ej.: identificación, cumplimentación, envío). */
        private String title; /** Título corto del paso. */
        private String description; /** Explicación del paso y contexto operativo. */

        /**
         * Acción que debe realizar el usuario en este paso.
         *
         * JSON: `user_action`
         */
        @JsonProperty("user_action")
        private String userAction;

        /**
         * Acción/respuesta esperada del portal o sistema.
         *
         * JSON: `portal_action`
         */
        @JsonProperty("portal_action")
        private String portalAction;

        /**
         * Documentos exigidos específicamente en este paso.
         *
         * JSON: `required_documents`
         */
        @JsonProperty("required_documents")
        private List<String> requiredDocuments;

        /**
         * Enlace oficial relacionado con este paso.
         *
         * JSON: `official_link`
         */
        @JsonProperty("official_link")
        private String officialLink;

        /**
         * Tiempo estimado para completar el paso (en minutos).
         *
         * JSON: `estimated_time_minutes`
         */
        @JsonProperty("estimated_time_minutes")
        private int estimatedTimeMinutes;
    }

    /**
     * Guía visual para acompañar un método de solicitud.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisualGuide {
        private String method; /** Método al que corresponde la guía visual. */
        private List<VisualStep> steps; /** Secuencia de pasos visuales de apoyo. */
    }

    /**
     * Paso visual individual con orientación de pantalla e imagen.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisualStep {
        private int step; /** Orden secuencial del paso visual. */
        private String phase; /** Fase del proceso a la que pertenece el paso visual. */
        private String title; /** Título resumido del paso visual. */
        private String description; /** Descripción textual de lo que debe observar o hacer el usuario. */

        /**
         * Pista de interfaz/pantalla para ubicar el punto de acción.
         *
         * JSON: `screen_hint`
         */
        @JsonProperty("screen_hint")
        private String screenHint;

        /**
         * Prompt o instrucción para generar/seleccionar apoyo gráfico.
         *
         * JSON: `image_prompt`
         */
        @JsonProperty("image_prompt")
        private String imagePrompt;

        /**
         * Enlace oficial de referencia para este paso visual.
         *
         * JSON: `official_link`
         */
        @JsonProperty("official_link")
        private String officialLink;
    }
}