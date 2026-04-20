package com.syntia.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bdns_regiones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BdnsRegion {

    @Id
    private Integer id;

    private String nombre;

    private String nivel;

    private Boolean activo;

    @Column(name = "sync_at")
    private LocalDateTime syncAt;
}

