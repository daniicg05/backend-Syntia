package com.syntia.ai.model.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

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
    /** ID de región del catálogo BDNS (nullable si aún no tiene datos de región). */
    private Integer regionId;
    /** ID de provincia del catálogo BDNS (nivel NUTS 3, hijo de region_id). */
    private Integer provinciaId;
    /** Tipos de beneficiario asociados a la convocatoria (del catálogo BDNS). */
    private List<String> tiposBeneficiario;
}
