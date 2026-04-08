package com.syntia.ai.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "perfiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Perfil {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @NotBlank(message = "El sector es obligatorio")
    @Column(nullable = false)
    private String sector;

    @NotBlank(message = "La ubicación es obligatoria")
    @Column(nullable = false)
    private String ubicacion;

    @Column(length = 255)
    private String empresa;

    @Column(length = 100)
    private String provincia;

    @Column(length = 30)
    private String telefono;

    @Column(name = "tipo_entidad")
    private String tipoEntidad;

    private String objetivos;

    @Column(name = "necesidades_financiacion")
    private String necesidadesFinanciacion;

    @Column(name = "descripcion_libre", columnDefinition = "TEXT")
    private String descripcionLibre;
}
