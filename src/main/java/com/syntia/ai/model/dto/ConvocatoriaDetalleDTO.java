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
}
