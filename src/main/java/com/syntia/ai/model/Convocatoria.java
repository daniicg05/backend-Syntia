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

    // ── Campos ampliados desde catálogo BDNS ──────────────────────────────

    @Column(name = "organo_nivel1", columnDefinition = "TEXT")
    private String organoNivel1;

    @Column(name = "organo_nivel2", columnDefinition = "TEXT")
    private String organoNivel2;

    @Column(name = "organo_nivel3", columnDefinition = "TEXT")
    private String organoNivel3;

    @Column(name = "sede_electronica", columnDefinition = "TEXT")
    private String sedeElectronica;

    @Column(name = "fecha_recepcion")
    private LocalDate fechaRecepcion;

    @Column(name = "instrumentos", columnDefinition = "TEXT")
    private String instrumentos;

    @Column(name = "tipo_convocatoria")
    private String tipoConvocatoria;

    @Column(name = "tipos_beneficiarios", columnDefinition = "TEXT")
    private String tiposBeneficiarios;

    @Column(name = "sectores", columnDefinition = "TEXT")
    private String sectores;

    @Column(name = "sectores_productos", columnDefinition = "TEXT")
    private String sectoresProductos;

    @Column(name = "regiones", columnDefinition = "TEXT")
    private String regiones;

    @Column(name = "descripcion_bases_reguladoras", columnDefinition = "TEXT")
    private String descripcionBasesReguladoras;

    @Column(name = "url_bases_reguladoras", columnDefinition = "TEXT")
    private String urlBasesReguladoras;

    @Column(name = "se_publica_diario_oficial")
    private Boolean sePublicaDiarioOficial;

    @Column(name = "fecha_inicio_solicitud")
    private LocalDate fechaInicioSolicitud;

    @Column(name = "fecha_fin_solicitud")
    private LocalDate fechaFinSolicitud;

    @Column(name = "text_inicio", columnDefinition = "TEXT")
    private String textInicio;

    @Column(name = "text_fin", columnDefinition = "TEXT")
    private String textFin;

    @Column(name = "ayuda_estado")
    private String ayudaEstado;

    @Column(name = "url_ayuda_estado", columnDefinition = "TEXT")
    private String urlAyudaEstado;

    @Column(name = "fondos", columnDefinition = "TEXT")
    private String fondos;

    @Column(name = "reglamento_descripcion", columnDefinition = "TEXT")
    private String reglamentoDescripcion;

    @Column(name = "reglamento_autorizacion")
    private Integer reglamentoAutorizacion;

    @Column(name = "objetivos", columnDefinition = "TEXT")
    private String objetivos;

    @Column(name = "anuncios", columnDefinition = "TEXT")
    private String anuncios;

    @Column(name = "documentos", columnDefinition = "TEXT")
    private String documentos;

    @Column(name = "advertencia", columnDefinition = "TEXT")
    private String advertencia;
}
