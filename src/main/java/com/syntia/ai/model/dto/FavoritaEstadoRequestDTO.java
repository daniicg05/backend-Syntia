package com.syntia.ai.model.dto;

import com.syntia.ai.model.EstadoSolicitudFavorita;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FavoritaEstadoRequestDTO {

    @NotNull(message = "estadoSolicitud es obligatorio")
    private EstadoSolicitudFavorita estadoSolicitud;
}

