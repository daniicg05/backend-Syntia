package com.syntia.ai.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RecomendacionDTO {
    private Long id;
    private int puntuacion;
    private String explicacion;
    private String guia;
    private String guiaEnriquecida;
    private Boolean usadaIa;
    private Boolean favorita;

    private Long convocatoriaId;
    private String titulo;
    private String tipo;
    private String sector;
    private String ubicacion;
    private String urlOficial;
    private String fuente;
    private LocalDate fechaCierre;
    private String organismo;
    private Double presupuesto;
    private LocalDate fechaPublicacion;
    private String numeroConvocatoria;
    private Boolean abierto;

    private boolean vigente;
}
