package com.syntia.ai.repository;

import com.syntia.ai.model.Convocatoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Convocatoria.
 */
@Repository
public interface ConvocatoriaRepository extends JpaRepository<Convocatoria, Long> {

    List<Convocatoria> findBySector(String sector);

    List<Convocatoria> findByUbicacion(String ubicacion);

    List<Convocatoria> findByTipo(String tipo);

    /**
     * Verifica si ya existe una convocatoria con el mismo titulo (ignorando mayusculas) y fuente.
     * Usado para evitar duplicados al importar desde BDNS.
     */
    boolean existsByTituloIgnoreCaseAndFuente(String titulo, String fuente);

    boolean existsByIdBdns(String idBdns);

    Optional<Convocatoria> findByIdBdns(String idBdns);

    /**
     * Busca una convocatoria por título (ignorando mayúsculas) y fuente.
     * Usado por el motor de matching para no duplicar convocatorias al persistir.
     */
    Optional<Convocatoria> findByTituloIgnoreCaseAndFuente(String titulo, String fuente);

    /**
     * Filtra convocatorias por sector y/o ubicación y/o tipo.
     * Los parámetros nulos o vacíos se ignoran.
     */
    @Query("SELECT c FROM Convocatoria c WHERE " +
            "(:sector IS NULL OR :sector = '' OR c.sector = :sector) AND " +
            "(:ubicacion IS NULL OR :ubicacion = '' OR c.ubicacion = :ubicacion) AND " +
            "(:tipo IS NULL OR :tipo = '' OR c.tipo = :tipo)")
    List<Convocatoria> filtrar(@Param("sector") String sector,
                               @Param("ubicacion") String ubicacion,
                               @Param("tipo") String tipo);

    /**
     * Devuelve los sectores distintos registrados (para el selector de filtros).
     */
    @Query("SELECT DISTINCT c.sector FROM Convocatoria c WHERE c.sector IS NOT NULL ORDER BY c.sector")
    List<String> findSectoresDistintos();

    /**
     * Devuelve los tipos distintos registrados (para el selector de filtros).
     */
    @Query("SELECT DISTINCT c.tipo FROM Convocatoria c WHERE c.tipo IS NOT NULL ORDER BY c.tipo")
    List<String> findTiposDistintos();

    /**
     * Devuelve las convocatorias cuyos títulos estén en la lista dada.
     * Usado por buscarEImportarDesdeBdns para devolver solo las relevantes a una búsqueda.
     */
    @Query("SELECT c FROM Convocatoria c WHERE c.titulo IN :titulos")
    List<Convocatoria> buscarPorTitulos(@Param("titulos") List<String> titulos);

    /**
     * Búsqueda local para modo gratuito: filtra por palabra clave en título/sector
     * y por ubicación (Nacional siempre incluido).
     * Usado por ConvocatoriaBdLocalService como alternativa a la API live de BDNS.
     */
    @Query("SELECT c FROM Convocatoria c WHERE " +
            "(:keyword IS NULL OR LOWER(c.titulo) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(c.sector) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:ubicacion IS NULL OR LOWER(c.ubicacion) = 'nacional' " +
            "   OR LOWER(c.ubicacion) LIKE LOWER(CONCAT('%', :ubicacion, '%')))")
    List<Convocatoria> buscarParaModoGratuito(@Param("keyword") String keyword,
                                              @Param("ubicacion") String ubicacion);

    // ── Queries de cobertura de campos ──────────────────────────────────────

    long countByOrganismoIsNotNull();

    long countByFechaPublicacionIsNotNull();

    long countByDescripcionIsNotNull();

    long countByTextoCompletoIsNotNull();

    long countBySectorIsNotNull();

    long countByFechaCierreIsNotNull();

    long countByUbicacionIsNotNull();

    long countByPresupuestoIsNotNull();

    long countByAbiertoIsNotNull();

    long countByFinalidadIsNotNull();

    long countByFechaInicioIsNotNull();

    long countByMrrIsNotNull();

    @Query("SELECT c FROM Convocatoria c WHERE c.id > :lastId AND c.numeroConvocatoria IS NOT NULL ORDER BY c.id ASC")
    List<Convocatoria> findEnriquecimientoBatch(@Param("lastId") Long lastId, Pageable pageable);

    long countByNumeroConvocatoriaIsNotNull();

    /**
     * Búsqueda pública full-text: filtra por keyword en título/descripción/sector
     * y opcionalmente por sector (LIKE). Usado por el endpoint público de búsqueda.
     */
    @Query("SELECT c FROM Convocatoria c WHERE " +
            "(:q IS NULL OR :q = '' OR " +
            "   LOWER(c.titulo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "   (c.descripcion IS NOT NULL AND LOWER(c.descripcion) LIKE LOWER(CONCAT('%', :q, '%'))) OR " +
            "   (c.sector IS NOT NULL AND LOWER(c.sector) LIKE LOWER(CONCAT('%', :q, '%')))) AND " +
            "(:sector IS NULL OR :sector = '' OR " +
            "   (c.sector IS NOT NULL AND LOWER(c.sector) LIKE LOWER(CONCAT('%', :sector, '%'))))")
    Page<Convocatoria> buscarPublico(@Param("q") String q,
                                     @Param("sector") String sector,
                                     Pageable pageable);

    /** Últimas convocatorias para la sección destacadas del Home. */
    List<Convocatoria> findTop16ByOrderByIdDesc();

    /**
     * Pool de candidatos para recomendaciones personalizadas.
     * Devuelve convocatorias cuyo sector/título contenga alguna keyword del usuario,
     * más un fallback de recientes. Limitado para scoring en memoria.
     */
    @Query("SELECT c FROM Convocatoria c WHERE " +
            "(:kw1 IS NOT NULL AND (LOWER(c.sector) LIKE LOWER(CONCAT('%',:kw1,'%')) OR LOWER(c.titulo) LIKE LOWER(CONCAT('%',:kw1,'%')))) OR " +
            "(:kw2 IS NOT NULL AND (LOWER(c.sector) LIKE LOWER(CONCAT('%',:kw2,'%')) OR LOWER(c.titulo) LIKE LOWER(CONCAT('%',:kw2,'%')))) OR " +
            "(:kw3 IS NOT NULL AND (LOWER(c.sector) LIKE LOWER(CONCAT('%',:kw3,'%')) OR LOWER(c.titulo) LIKE LOWER(CONCAT('%',:kw3,'%')))) " +
            "ORDER BY c.id DESC")
    List<Convocatoria> buscarCandidatosPorKeywords(@Param("kw1") String kw1,
                                                    @Param("kw2") String kw2,
                                                    @Param("kw3") String kw3,
                                                    Pageable pageable);
}

