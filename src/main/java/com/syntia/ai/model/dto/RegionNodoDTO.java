package com.syntia.ai.model.dto;

import java.util.List;

/**
 * Nodo del árbol jerárquico de regiones BDNS.
 * Usado para poblar el selector de región en el frontend.
 */
public record RegionNodoDTO(Long id, String descripcion, List<RegionNodoDTO> children) {}