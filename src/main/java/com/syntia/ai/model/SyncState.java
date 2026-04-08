package com.syntia.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sync_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncState {

    public enum Estado {
        PENDIENTE, EN_PROGRESO, COMPLETADO, ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String eje;

    @Column(name = "ultima_pagina_ok")
    private int ultimaPaginaOk;

    @Column(name = "total_paginas")
    private Integer totalPaginas;

    @Column(name = "registros_nuevos")
    private int registrosNuevos;

    @Column(name = "registros_actualizados")
    private int registrosActualizados;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "ts_inicio")
    private Instant tsInicio;

    @Column(name = "ts_ultima_carga")
    private Instant tsUltimaCarga;
}