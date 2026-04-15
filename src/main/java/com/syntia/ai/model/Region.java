package com.syntia.ai.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Región geográfica del catálogo BDNS.
 * Estructura jerárquica NUTS: España → macro-región → CCAA → provincia.
 * Los IDs son los mismos que devuelve la API /regiones de BDNS (no auto-generados).
 */
@Entity
@Table(name = "regiones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Region {

    /** ID numérico del catálogo BDNS (p.ej. 26 = Comunidad de Madrid). */
    @Id
    private Long id;

    /** Descripción legible (p.ej. "ES30 - COMUNIDAD DE MADRID"). */
    @Column(nullable = false)
    private String descripcion;

    /** ID del nodo padre (null para nodos raíz como "ES - ESPAÑA"). */
    @Column(name = "parent_id")
    private Long parentId;
}