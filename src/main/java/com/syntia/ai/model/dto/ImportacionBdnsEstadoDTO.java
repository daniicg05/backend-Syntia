package com.syntia.ai.model.dto;

import java.time.LocalDateTime;

public record ImportacionBdnsEstadoDTO(
        String estado,
        int paginaActual,
        int registrosImportados,
        LocalDateTime iniciadoEn,
        LocalDateTime finalizadoEn,
        String error
) {}