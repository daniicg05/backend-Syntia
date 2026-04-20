package com.syntia.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvocatoriaDetalleDTO {
    // Compatibilidad legacy con controladores existentes
    private Long id;
    private String codigoBdns;
    private String descripcion;
    private List<String> tiposBeneficiario;

    // Identificacion
    private String idBdns;
    private String numeroConvocatoria;
    private String titulo;
    private String tituloAlternativo;

    // Clasificacion
    private String tipo;
    private String ubicacion;
    private String sector;
    private String finalidad;
    private String instrumento;

    // Organismo
    private String nivel1;
    private String nivel2;
    private String nivel3;
    private String fuente;

    // Contenido
    private String objeto;
    private String beneficiarios;
    private String requisitos;
    private String documentacion;

    // Financiero
    private String dotacion;
    private String ayudaEstado;
    private Boolean mrr;
    private Boolean contribucion;

    // Plazos
    private LocalDate fechaRecepcion;
    private LocalDate fechaFinSolicitud;
    private LocalDate fechaCierre;
    private String plazoSolicitudes;

    // Procedimiento
    private String procedimiento;
    private String basesReguladoras;
    private String urlOficial;

    // Datos IA Syntia (opcionales)
    private Integer puntuacion;
    private String explicacion;
    private String guia;
    private LocalDateTime fechaAnalisis;


    // ── Campos ampliados catálogo BDNS ────────────────────────────────────
    private String sedeElectronica;
    private String tipoConvocatoria;
    private Double presupuestoTotal;
    private String descripcionBasesReguladoras;
    private String urlBasesReguladoras;
    private Boolean sePublicaDiarioOficial;
    private LocalDate fechaInicioSolicitud;
    private String textInicio;
    private String textFin;
    private String urlAyudaEstado;
    private String reglamentoDescripcion;
    private Integer reglamentoAutorizacion;
    private String advertencia;

    private List<String> instrumentos;
    private List<TipoBeneficiarioDTO> tiposBeneficiarios;
    private List<SectorDTO> sectores;
    private List<SectorDTO> sectoresProductos;
    private List<String> regiones;
    private List<String> fondos;
    private List<String> objetivos;
    private List<AnuncioDTO> anuncios;
    private List<DocumentoDTO> documentos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TipoBeneficiarioDTO {
        private String codigo;
        private String descripcion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorDTO {
        private String codigo;
        private String descripcion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnuncioDTO {
        private Integer numAnuncio;
        private String titulo;
        private String texto;
        private String url;
        private String cve;
        private String desDiarioOficial;
        private LocalDate datPublicacion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentoDTO {
        private Integer id;
        private String nombreFic;
        private String descripcion;
        private Integer longitud;
        private LocalDate datMod;
        private LocalDate datPublicacion;
    }
}
