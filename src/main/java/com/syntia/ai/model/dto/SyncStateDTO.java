package com.syntia.ai.model.dto;

import java.time.Instant;

public record SyncStateDTO(
        String eje,
        String estado,
        int ultimaPaginaOk,
        int registrosNuevos,
        int registrosActualizados,
        Instant tsInicio,
        Instant tsUltimaCarga
) {}