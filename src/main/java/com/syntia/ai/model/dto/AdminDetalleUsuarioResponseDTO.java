package com.syntia.ai.model.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDetalleUsuarioResponseDTO {
    private AdminUsuarioDetalleDTO usuario;
    private List<Map<String, Object>> proyectos;
    private Map<Long, Long> recsPerProyecto;
    private boolean emailCambiado;
    private List<HistorialCorreoDTO> historialCorreo;
}

