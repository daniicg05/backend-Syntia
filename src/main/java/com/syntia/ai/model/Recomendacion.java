package com.syntia.ai.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA Entidad JPAque representa una recomendación generada para un proyecto
 * en el contexto de una convocatoria.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 * <li>Persistir la relación entre un {@code Proyecto} y una {@code Convocatoria}.</li>
 * <li>Guardar el resultado de evaluación \(`puntuacion`\).</li>
 * <li>Almacenar textos explicativos y guías generadas.</li>
 * <li>Registrar metadatos de generación \(`usadaIa`, `generadaEn`\).</li>
 * </ul>
 *
 * <p>Mapeo:</p>
 * <ul>
 * <li>Tabla: {@code recomendaciones}</li>
 * <li>Clave primaria autogenerada \(`IDENTITY`\).</li>
 * <li>Relaciones {@code @ManyToOne} con carga diferida \(`LAZY`\).</li>
 * </ul>
 */
@Entity
@Table(name = "recomendaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recomendacion {

    /**
     * Identificador único de la recomendación.
     * Se genera automáticamente al persistir.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Proyecto al que pertenece esta recomendación.
     * Relación muchos\-a\-uno con carga diferida para optimizar consultas.
     * No puede ser nulo a nivel de base de datos.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    /**
     * Convocatoria contra la cual se evaluó el proyecto.
     * Relación muchos\-a\-uno con carga diferida.
     * No puede ser nula.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convocatoria_id", nullable = false)
    private Convocatoria convocatoria;

    /**
     * Puntuación numérica resultante de la recomendación.
     * Campo obligatorio.
     */
    @Column(nullable = false)
    private int puntuacion;

    /**
     * Explicación textual del porqué de la puntuación o recomendación.
     * Se guarda como `TEXT` para permitir contenido extenso.
     */
    @Column(columnDefinition = "TEXT")
    private String explicacion;

    /**
     * Guía base o resumen de acciones sugeridas.
     * Se persiste como `TEXT`.
     */
    @Column(columnDefinition = "TEXT")
    private String guia;

    /**
     * Versión enriquecida de la guía, potencialmente más detallada.
     * Se mapea como `TEXT` y en BD se guarda como `guia_enriquecida`.
     */
    @Column(name = "guia_enriquecida", columnDefinition = "TEXT")
    private String guiaEnriquecida;

    /**
     * Indica si en la generación/intervención de esta recomendación se utilizó IA.
     *
     * <p>Con `@Builder.Default` se conserva el valor por defecto \(`false`\)
     * al construir con Lombok Builder si no se informa explícitamente.</p>
     */
    @Column(name = "usada_ia", nullable = false)
    @Builder.Default
    private boolean usadaIa = false;

    /**
     * Fecha y hora de creación de la recomendación.
     * No puede ser nula y no es actualizable tras persistencia.
     */
    @Column(name = "generada_en", nullable = false, updatable = false)
    private LocalDateTime generadaEn;

    /**
     * Callback JPA ejecutado antes de insertar la entidad.
     * Inicializa la marca temporal de creación.
     */
    @PrePersist
    protected void onCreate() {
        this.generadaEn = LocalDateTime.now();
    }
}