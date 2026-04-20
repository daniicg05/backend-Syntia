package com.syntia.ai.model;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "convocatorias",
        indexes = {
                @Index(name = "idx_convocatorias_id_bdns", columnList = "id_bdns", unique = true),
                @Index(name = "idx_convocatorias_numero_convocatoria", columnList = "numero_convocatoria")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Convocatoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String titulo;

    private String tipo;

    private String sector;

    @Column(columnDefinition = "TEXT")
    private String ubicacion;

    @Column(name = "url_oficial", columnDefinition = "TEXT")
    private String urlOficial;

    @Column(columnDefinition = "TEXT")
    private String fuente;

    @Column(name = "id_bdns")
    private String idBdns;

    @Column(name = "numero_convocatoria")
    private String numeroConvocatoria;

    @Column(name = "fecha_cierre")
    private LocalDate fechaCierre;

    @Column(columnDefinition = "TEXT")
    private String organismo;

    @Column(name = "fecha_publicacion")
    private LocalDate fechaPublicacion;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "texto_completo", columnDefinition = "TEXT")
    private String textoCompleto;

    @Column(name = "mrr")
    private Boolean mrr;

    @Column
    private Double presupuesto;

    @Column
    private Boolean abierto;

    @Column(columnDefinition = "TEXT")
    private String finalidad;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    /** ID numérico de región del catálogo BDNS (FK lógica a tabla regiones). */
    @Column(name = "region_id")
    private Integer regionId;

    /** ID numérico de provincia del catálogo BDNS (nivel NUTS 3, hijo de region_id). */
    @Column(name = "provincia_id")
    private Integer provinciaId;
}
