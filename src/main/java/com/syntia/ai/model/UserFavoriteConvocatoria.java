package com.syntia.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_favorite_convocatorias",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fav_user_convocatoria", columnNames = {"user_id", "convocatoria_id"})
        },
        indexes = {
                @Index(name = "idx_fav_user_guardada_en", columnList = "user_id,guardada_en"),
                @Index(name = "idx_fav_user_estado", columnList = "user_id,estado_solicitud"),
                @Index(name = "idx_fav_user_convocatoria", columnList = "user_id,convocatoria_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFavoriteConvocatoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Usuario usuario;

    @Column(name = "convocatoria_id", nullable = false)
    private Long convocatoriaId;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(length = 500)
    private String organismo;

    @Column(length = 500)
    private String ubicacion;

    @Column(length = 120)
    private String tipo;

    @Column(length = 120)
    private String sector;

    @Column(name = "fecha_publicacion")
    private LocalDate fechaPublicacion;

    @Column(name = "fecha_cierre")
    private LocalDate fechaCierre;

    @Column(precision = 18, scale = 2)
    private BigDecimal presupuesto;

    private Boolean abierto;

    @Column(name = "url_oficial", columnDefinition = "TEXT")
    private String urlOficial;

    @Column(name = "id_bdns", length = 120)
    private String idBdns;

    @Column(name = "numero_convocatoria", length = 120)
    private String numeroConvocatoria;

    @Column(name = "estado_solicitud", nullable = false, length = 30)
    @Convert(converter = EstadoSolicitudFavoritaConverter.class)
    @Builder.Default
    private EstadoSolicitudFavorita estadoSolicitud = EstadoSolicitudFavorita.NO_SOLICITADA;

    @Column(name = "guardada_en", nullable = false)
    private LocalDateTime guardadaEn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (guardadaEn == null) {
            guardadaEn = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

