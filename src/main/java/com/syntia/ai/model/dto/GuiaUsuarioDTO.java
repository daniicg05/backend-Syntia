package com.syntia.ai.model.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuiaUsuarioDTO {
    private Long id;
    private String origen;  // "recomendacion" o "analisis"

    // Datos de la convocatoria
    private Long convocatoriaId;
    private String titulo;
    private String organismo;
    private String sector;
    private String ubicacion;
    private LocalDate fechaCierre;
    private Boolean abierto;
    private String urlOficial;
    private String numeroConvocatoria;

    // Datos del proyecto asociado (puede ser null)
    private Long proyectoId;
    private String proyectoNombre;

    // La guía en sí
    private GuiaSubvencionDTO guia;

    // Metadatos
    private LocalDateTime creadoEn;
    private int puntuacion;
}
