package com.syntia.ai.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecomendacionDTO {
    private Long id;
    private int puntuacion;
    private String explicacion;
    private String guia;
    private String guiaEnriquecida;
    private String usadaIa;

    private Long convocatoriaId;
    private String titulo;
    private String tipo;
    private String sector;
    private String ubicacion;
    private String urlOficial;
    private String fuente;
    private String fechaCierre;

    private boolean vigente;
}
