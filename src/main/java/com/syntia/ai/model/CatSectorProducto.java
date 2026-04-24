package com.syntia.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cat_sectores_productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatSectorProducto {
    @Id
    Integer id;

    @Column(nullable = false, length = 200)
    String descripcion;
}
