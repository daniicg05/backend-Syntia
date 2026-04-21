package com.syntia.ai.model.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * DTO completo para el análisis IA enriquecido de una convocatoria BDNS.
 * FICHERO NUEVO — no modifica ningún DTO existente.
 *
 * Bloques 1-9 : campos extraídos directamente de la API BDNS.
 * Bloque 10   : campos generados por OpenAI gpt-4.1.
 */
@Data
@Builder
public class ConvocatoriaAnalisisIADTO {

    // ─────────────────────────────────────────
    // BLOQUE 1 — Identificación (BDNS)
    // ─────────────────────────────────────────
    private Long    idBdns;
    private String  numeroConvocatoria;
    private String  titulo;
    private String  tituloCooficial;

    // ─────────────────────────────────────────
    // BLOQUE 2 — Órgano convocante (BDNS)
    // ─────────────────────────────────────────
    private String  organoConvocante;
    private String  tipoAdministracion;
    private String  sedeElectronica;

    // ─────────────────────────────────────────
    // BLOQUE 3 — Datos económicos (BDNS)
    // ─────────────────────────────────────────
    private Double  presupuestoTotal;
    private String  instrumento;
    private String  tipoConvocatoria;
    private String  fechaRegistro;

    // ─────────────────────────────────────────
    // BLOQUE 4 — Ámbito y beneficiarios (BDNS)
    // ─────────────────────────────────────────
    private String  tipoBeneficiario;
    private String  sectorEconomico;
    private String  regionImpacto;
    private String  finalidad;
    private Boolean mecanismoRecuperacion;

    // ─────────────────────────────────────────
    // BLOQUE 5 — Solicitud (BDNS)
    // ─────────────────────────────────────────
    private Boolean extractoEnDiarioOficial;
    private Boolean solicitudIndefinida;
    private String  fechaInicioSolicitud;
    private String  fechaFinSolicitud;

    // ─────────────────────────────────────────
    // BLOQUE 6 — Ayudas de Estado / de minimis (BDNS)
    // ─────────────────────────────────────────
    private String  reglamentoUE;
    private String  saNumber;
    private String  saNumberEnlaceUE;
    private Boolean cofinanciadoFondosUE;
    private String  sectorProductos;
    private String  objetivos;

    // ─────────────────────────────────────────
    // BLOQUE 7 — Bases reguladoras (BDNS)
    // ─────────────────────────────────────────
    private String  basesReguladoras;
    private String  urlBasesReguladoras;

    // ─────────────────────────────────────────
    // BLOQUE 8 — Documentos (BDNS)
    // ─────────────────────────────────────────
    private List<DocumentoDTO> documentos;

    // ─────────────────────────────────────────
    // BLOQUE 9 — Extractos / Diario Oficial (BDNS)
    // ─────────────────────────────────────────
    private List<ExtractoDTO> extractos;

    // ─────────────────────────────────────────
    // BLOQUE 10 — Análisis IA (OpenAI gpt-4.1)
    // ─────────────────────────────────────────
    private Integer puntuacionCompatibilidad;
    private String  resumenEjecutivo;
    private String  descripcionObjetivo;
    private String  requisitosElegibilidad;
    private String  cuantiaDetalle;
    private String  plazoPresentacion;
    private String  formaPresentacion;
    private String  documentacionRequerida;
    private String  procedimientoResolucion;
    private String  criteriosValoracion;
    private String  obligacionesBeneficiario;
    private String  incompatibilidades;
    private String  contactoGestion;
    private String  advertenciasClave;
    private String  sectorInferido;
    private String  urlOficial;

    // ─────────────────────────────────────────
    // SUB-DTOs
    // ─────────────────────────────────────────
    @Data
    @Builder
    public static class DocumentoDTO {
        private String fechaRegistro;
        private String fechaPublicacion;
        private String nombre;
        private String urlDescarga;
    }

    @Data
    @Builder
    public static class ExtractoDTO {
        private String diarioOficial;
        private String fechaPublicacion;
        private String tituloAnuncio;
        private String tituloCooficial;
        private String url;
    }
}