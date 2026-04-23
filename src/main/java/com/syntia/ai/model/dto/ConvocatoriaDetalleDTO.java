package com.syntia.ai.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ConvocatoriaDetalleDTO {
    private Long id;
    private String titulo;
    private String tipo;
    private String sector;
    private String ubicacion;
    private String organismo;
    private String descripcion;
    private String textoCompleto;
    private String urlOficial;
    private String idBdns;
    private String numeroConvocatoria;
    private String finalidad;
    private Double presupuesto;
    private Boolean abierto;
    private Boolean mrr;
    private LocalDate fechaCierre;
    private LocalDate fechaPublicacion;
    private LocalDate fechaInicio;
    private Integer regionId;
    private Integer provinciaId;

    // Datos resueltos de catálogos BDNS
    private List<String> tiposBeneficiario;
    private List<String> finalidades;
    private List<String> instrumentos;
    private List<String> organos;
    private List<String> regiones;
    private String tipoAdmin;
    private List<String> actividades;
    private List<String> reglamentos;
    private List<String> objetivos;
    private List<String> sectoresProducto;
}
