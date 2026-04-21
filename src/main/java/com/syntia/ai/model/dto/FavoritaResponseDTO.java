package com.syntia.ai.model.dto;

import com.syntia.ai.model.EstadoSolicitudFavorita;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class FavoritaResponseDTO {
    private Long id;
    private String titulo;
    private String organismo;
    private String ubicacion;
    private String tipo;
    private String sector;
    private LocalDate fechaPublicacion;
    private LocalDate fechaCierre;
    private BigDecimal presupuesto;
    private Boolean abierto;
    private String urlOficial;
    private String idBdns;
    private String numeroConvocatoria;
    private EstadoSolicitudFavorita estadoSolicitud;
    private LocalDateTime guardadaEn;
}

