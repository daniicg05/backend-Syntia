package com.syntia.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sync_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ejecucion_id", nullable = false)
    private String ejecucionId;

    @Column(nullable = false)
    private String eje;

    private int pagina;

    @Column(name = "registros_nuevos")
    private int registrosNuevos;

    @Column(name = "registros_actualizados")
    private int registrosActualizados;

    private int errores;

    private Instant ts;
}