package com.syntia.ai.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import javax.annotation.processing.Generated;
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
    private Long id;

    /**
     * Nombre del usuario.
     *
     * <p>Representa el nombre visible o principal del usuario.</p>
     */
    private String nombre;

    /**
     * Correo electrónico del usuario.
     *
     * <p>Se recomienda garantizar unicidad y formato válido desde capa de validación * o mediante restricciones de base de datos.</p>
     */
    private String email;
}
