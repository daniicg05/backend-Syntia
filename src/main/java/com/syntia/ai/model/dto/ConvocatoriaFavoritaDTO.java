package com.syntia.ai.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ConvocatoriaFavoritaDTO {
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

    private LocalDateTime fechaFavorita;
}

