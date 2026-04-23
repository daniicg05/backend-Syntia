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
@Table(name = "cat_organos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatOrgano {
    @Id
    Integer id;

    @Column(nullable = false, length = 300)
    String descripcion;

    @Column(name = "parent_id")
    Integer parentId; // null = root

    @Column(name = "tipo_admin", length = 1)
    String tipoAdmin; // C, A, L, O
}
