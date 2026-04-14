package com.syntia.ai.model.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvocatoriaPublicaDTO {
    private Long id;
    private String titulo;
    private String tipo;
    private String sector;
    private String organismo;
    private String ubicacion;
    private LocalDate fechaCierre;
    private LocalDate fechaPublicacion;
    private Boolean abierto;
    private String urlOficial;
    private String idBdns;
    private String numeroConvocatoria;
    /** Puntuación de afinidad 0-100. Null en endpoints públicos sin autenticación. */
    private Integer matchScore;
    /** Razón breve del match. Null en endpoints públicos. */
    private String matchRazon;
    private Double presupuesto;
}
