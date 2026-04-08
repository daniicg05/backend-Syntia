package com.syntia.ai.model.dto;

import java.time.Instant;

public record ResumenEjecucionDTO(
        String ejecucionId,
        Instant tsInicio,
        Instant tsFin,
        long totalRegistrosNuevos,
        long totalRegistrosActualizados,
        long totalErrores,
        long ejesProcesados,
        long totalPaginas
) {}