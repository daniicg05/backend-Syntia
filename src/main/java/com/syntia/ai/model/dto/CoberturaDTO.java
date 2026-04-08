package com.syntia.ai.model.dto;

import java.util.List;

/**
 * Métricas de cobertura de campos sobre el total de convocatorias en BD.
 * Cada entrada de {@code campos} indica qué % del total tiene ese campo relleno.
 */
public record CoberturaDTO(
        long totalConvocatorias,
        List<CampoCobertura> campos
) {
    public record CampoCobertura(String campo, long conValor, double porcentaje) {}
}