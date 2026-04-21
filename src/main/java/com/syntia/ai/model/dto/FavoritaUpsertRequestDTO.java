package com.syntia.ai.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class FavoritaUpsertRequestDTO {

    @NotNull(message = "convocatoriaId es obligatorio")
    @Positive(message = "convocatoriaId debe ser mayor que 0")
    private Long convocatoriaId;

    @NotBlank(message = "titulo es obligatorio")
    @Size(max = 500, message = "titulo supera el maximo permitido")
    private String titulo;

    @Size(max = 500, message = "organismo supera el maximo permitido")
    private String organismo;

    @Size(max = 500, message = "ubicacion supera el maximo permitido")
    private String ubicacion;

    @Size(max = 120, message = "tipo supera el maximo permitido")
    private String tipo;

    @Size(max = 120, message = "sector supera el maximo permitido")
    private String sector;

    private LocalDate fechaPublicacion;

    private LocalDate fechaCierre;

    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal presupuesto;

    private Boolean abierto;

    @Size(max = 4000, message = "urlOficial supera el maximo permitido")
    private String urlOficial;

    @Size(max = 120, message = "idBdns supera el maximo permitido")
    private String idBdns;

    @Size(max = 120, message = "numeroConvocatoria supera el maximo permitido")
    private String numeroConvocatoria;
}

