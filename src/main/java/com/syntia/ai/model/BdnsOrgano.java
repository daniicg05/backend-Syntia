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
@Table(name = "bdns_organos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BdnsOrgano {

    @Id
    private Integer id;

    private String nombre;

    @Column(name = "tipo_admon")
    private String tipoAdmon;

    private Boolean activo;

    @Column(name = "sync_at")
    private LocalDateTime syncAt;
}

