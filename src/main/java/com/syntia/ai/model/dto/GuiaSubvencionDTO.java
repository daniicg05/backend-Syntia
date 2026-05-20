package com.syntia.ai.model.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO que modela la guía completa de solicitud de una subvención,
 * generada por OpenAI siguiendo el esquema visual de galería interactiva.
 *
 * Estructura JSON devuelta por la IA con fases: resumen, métodos de solicitud,
 * documentos, requisitos universales LGS art. 13, workflows por método,
 * guías visuales con prompts para imágenes y disclaimer legal.
 *
 * Referencia normativa: Ley 38/2003 General de Subvenciones, RD 887/2006,
 * Ley 39/2015 LPACAP.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GuiaSubvencionDTO {

    @Valid
    @JsonProperty("grant_summary")
    private GrantSummary grantSummary;

    @Valid
    @Builder.Default
    @JsonProperty("application_methods")
    private List<ApplicationMethod> applicationMethods = new ArrayList<>();

    @Builder.Default
    @JsonProperty("required_documents")
    private List<String> requiredDocuments = new ArrayList<>();

    @Valid
    @Builder.Default
    @JsonProperty("structured_documents")
    private List<StructuredDocument> structuredDocuments = new ArrayList<>();

    @Builder.Default
    @JsonProperty("universal_requirements_lgs_art13")
    private List<String> universalRequirementsLgsArt13 = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<Workflow> workflows = new ArrayList<>();

    @Valid
    @Builder.Default
    @JsonProperty("visual_guides")
    private List<VisualGuide> visualGuides = new ArrayList<>();

    @JsonProperty("legal_disclaimer")
    private String legalDisclaimer;

    @Valid
    private Deadlines deadlines;

    @Valid
    private OfficialInfo official;

    @Valid
    @JsonProperty("ai_analysis")
    private AiAnalysis aiAnalysis;

    @Valid
    @JsonProperty("visual_identity")
    private VisualIdentity visualIdentity;

    @Valid
    @Builder.Default
    @JsonProperty("visual_references")
    private List<VisualReference> visualReferences = new ArrayList<>();

    // ── Clases internas ──

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GrantSummary {

        private String title;
        private String organism;
        private String objective;

        @JsonProperty("who_can_apply")
        private String whoCanApply;

        private String deadline;

        @JsonProperty("official_link")
        private String officialLink;

        @JsonProperty("legal_basis")
        private String legalBasis;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicationMethod {

        private String method;
        private String description;

        @JsonProperty("official_portal")
        private String officialPortal;

        private String entity;

        @JsonProperty("visual_type")
        private String visualType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Workflow {

        private String method;

        @Valid
        @Builder.Default
        private List<WorkflowStep> steps = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkflowStep {

        private Integer step;
        private String phase;
        private String title;
        private String description;

        @JsonProperty("user_action")
        private String userAction;

        @JsonProperty("portal_action")
        private String portalAction;

        @Builder.Default
        @JsonProperty("required_documents")
        private List<String> requiredDocuments = new ArrayList<>();

        @JsonProperty("official_link")
        private String officialLink;

        @JsonProperty("estimated_time_minutes")
        private Integer estimatedTimeMinutes;

        @JsonProperty("action_type")
        private String actionType;

        @JsonProperty("portal_entity")
        private String portalEntity;

        @JsonProperty("visual_type")
        private String visualType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StructuredDocument {

        private String name;
        private String description;
        private String type;
        private String format;
        private Boolean required;
        private String entity;

        @JsonProperty("visual_type")
        private String visualType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisualGuide {

        private String method;

        @Valid
        @Builder.Default
        private List<VisualStep> steps = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisualStep {

        private Integer step;
        private String phase;
        private String title;
        private String description;

        @JsonProperty("screen_hint")
        private String screenHint;

        @JsonProperty("image_prompt")
        private String imagePrompt;

        @JsonProperty("official_link")
        private String officialLink;

        private String entity;

        @JsonProperty("visual_type")
        private String visualType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Deadlines {

        private String opening;
        private String closing;
        private String resolution;
        private String notes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfficialInfo {

        @JsonProperty("organism_name")
        private String organismName;

        @JsonProperty("official_url")
        private String officialUrl;

        @JsonProperty("portal_domain")
        private String portalDomain;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisualIdentity {

        private String entity;
        private String domain;
        private String kind;
        private String scope;

        @JsonProperty("official_url")
        private String officialUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisualReference {

        private String section;
        private String entity;

        @JsonProperty("visual_type")
        private String visualType;

        private String domain;

        @JsonProperty("official_url")
        private String officialUrl;

        private String label;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiAnalysis {

        @JsonProperty("suitability_score")
        private Integer suitabilityScore;

        @Builder.Default
        private List<String> opportunities = new ArrayList<>();

        @Builder.Default
        private List<String> risks = new ArrayList<>();
    }
}
