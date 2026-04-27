package com.syntia.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalisisCompletoDTO {

    private Compatibilidad compatibilidad;

    @Builder.Default
    private List<Slide> slides = new ArrayList<>();

    private Recursos recursos;

    private String disclaimer;

    // ── Compatibilidad ──

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Compatibilidad {
        private String nivel;       // ALTA, MEDIA, BAJA, NO_EVALUABLE
        private int puntuacion;     // 0-100
        private String explicacion; // 2-3 sentences
    }

    // ── Slide ──

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Slide {
        private String tipo;       // resumen, elegibilidad, gastos, criterios, documentacion,
                                   // procedimiento, plazos, obligaciones, justificacion, advertencias
        private String titulo;
        private String icono;      // emoji
        private String contenido;  // paragraph summary

        @Builder.Default
        private List<Item> items = new ArrayList<>();

        private String consejo;    // personalized advice (only if profile provided)
        private String alerta;     // personalized warning (only if profile provided)
    }

    // ── Item inside a Slide ──

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String titulo;
        private String descripcion;
        private String tipo;       // requisito, documento, paso, criterio, plazo, obligacion, consejo, advertencia, dato
        private String estado;     // cumple, no_cumple, verificar, null
        private String url;        // official URL if applicable
        private Integer peso;      // weight % (only for criteria)

        @JsonProperty("tiempo_minutos")
        private Integer tiempoMinutos; // estimated time (only for steps)

        @Builder.Default
        @JsonProperty("sub_items")
        private List<String> subItems = new ArrayList<>();
    }

    // ── Recursos ──

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Recursos {
        @JsonProperty("url_convocatoria")
        private String urlConvocatoria;

        @JsonProperty("url_bases_reguladoras")
        private String urlBasesReguladoras;

        @JsonProperty("url_sede_electronica")
        private String urlSedeElectronica;

        @Builder.Default
        private List<Documento> documentos = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Documento {
        private String nombre;
        private String descripcion;
        private String url;
    }
}
