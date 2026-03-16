package com.syntia.ai.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidad que representa un proyecto descrito por un usuario.
 * Adaptada para usarse en una API REST.
 */
@Entity
@Table(name = "proyectos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relación con el usuario propietario del proyecto.
     * Se ignoran propiedades problemáticas de Hibernate/Jackson.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnoreProperties({"proyectos", "hibernateLazyInitializer", "handler"})
    private Usuario usuario;

    @NotBlank(message = "El nombre del proyecto es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    @Column(nullable = false, length = 255)
    private String nombre;

    @Size(max = 100, message = "El sector no puede superar los 100 caracteres")
    @Column(length = 100)
    private String sector;

    @Size(max = 150, message = "La ubicación no puede superar los 150 caracteres")
    @Column(length = 150)
    private String ubicacion;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /**
     * Se ignora en la serialización JSON para evitar recursividad
     * y respuestas demasiado pesadas en la API.
     */
    @Builder.Default
    @JsonIgnore
    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Recomendacion> recomendaciones = new ArrayList<>();
}