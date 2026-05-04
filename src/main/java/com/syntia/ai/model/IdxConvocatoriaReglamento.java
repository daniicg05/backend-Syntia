package com.syntia.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idx_convocatoria_reglamento",
       uniqueConstraints = @UniqueConstraint(columnNames = {"numero_convocatoria", "reglamento_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdxConvocatoriaReglamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "numero_convocatoria", nullable = false, length = 20)
    String numeroConvocatoria;

    @Column(name = "reglamento_id", nullable = false)
    Integer reglamentoId;
}
