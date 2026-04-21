
package com.syntia.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.syntia.ai.model.EstadoSolicitudFavorita;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class FavoritaItemImportDTO {

    @JsonAlias({"id", "convocatoriaId"})
    @NotNull(message = "id es obligatorio")
    @Positive(message = "id debe ser mayor que 0")
    private Long convocatoriaId;

    @NotBlank(message = "titulo es obligatorio")
    @Size(max = 500, message = "titulo supera el maximo permitido")
    private String titulo;

    @Size(max = 500)
    private String organismo;

    @Size(max = 500)
    private String ubicacion;

    @Size(max = 120)
    private String tipo;

    @Size(max = 120)
    private String sector;

    private LocalDate fechaPublicacion;

    private LocalDate fechaCierre;

    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal presupuesto;

    private Boolean abierto;

    @Size(max = 4000)
    private String urlOficial;

    @Size(max = 120)
    private String idBdns;

    @Size(max = 120)
    private String numeroConvocatoria;

    private EstadoSolicitudFavorita estadoSolicitud;

    private Instant guardadaEn;
}

