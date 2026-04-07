package com.syntia.ai.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import javax.annotation.processing.Generated;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un usuario dentro del sistema.
 *
 * <p>Esta clase se mapeará automáticamente a una tabla en la base de datos * (por defecto, con nombre similar al de la clase: {@code usuario} o {@code Usuario},
 * según la estrategia de naming configurada en JPA/Hibernate).</p>
 *
 * <p>Responsabilidad principal:</p>
 * <ul>
 * <li>Modelar la información básica de un usuario.</li>
 * <li>Servir como objeto persistente para operaciones CRUD.</li>
 * </ul>
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    /**
     * Identificador único del usuario.
     *
     * <p>Marcado como clave primaria con {@link Id}.</p>
     * <p>Se genera automáticamente mediante estrategia {@link GenerationType#IDENTITY},
     * lo que normalmente delega en una columna autoincremental de la base de datos.</p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Correo electrónico del usuario.
     *
     * <p>Se recomienda garantizar unicidad y formato válido desde capa de validación
     * o mediante restricciones de base de datos.</p>
     */
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe tener un formato válido")
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * Contraseña del usuario almacenada en formato seguro \(hash\).
     *
     * <p>Se persiste en la columna `password_hash` y es un dato obligatorio.</p>
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Column(name = "password_hash", nullable = false)
    private String password;

    /**
     * Rol asignado al usuario dentro del sistema.
     *
     * <p>Se guarda como texto en base de datos usando `EnumType.STRING`
     * para mejorar legibilidad y evitar dependencias por posición ordinal.</p>
     */
    @NotNull(message = "El rol es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'GRATUITO'")
    @Builder.Default
    private Plan plan = Plan.GRATUITO;

    /**
     * Fecha y hora de creación del registro.
     *
     * <p>Se establece automáticamente al persistir por primera vez y no es actualizable.</p>
     */
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /**
     * Callback ejecutado antes de persistir la entidad por primera vez.
     *
     * <p>Inicializa `creadoEn` con la fecha y hora actual del sistema.</p>
     */
    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }
}