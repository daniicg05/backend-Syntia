package com.syntia.ai.model.dto;

import java.time.LocalDateTime;

public record ImportacionBdnsEstadoDTO(
        String estado,
        int registrosImportados,
        String ejeActual,
        LocalDateTime iniciadoEn,
        LocalDateTime finalizadoEn,
        String error,
        String modo
) {}