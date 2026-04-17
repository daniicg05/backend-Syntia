package com.syntia.ai.repository;

import com.syntia.ai.model.Recomendacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecomendacionRepository extends JpaRepository<Recomendacion, Integer> {

    /**
     * Obtiene todas las recomendaciones de un proyecto ordenadas por puntuación descendente.
     * Usa JOIN FETCH para cargar la convocatoria en la misma query y evitar
     * LazyInitializationException al convertir a DTO.
     */

    @Query("SELECT r FROM Recomendacion r JOIN FETCH r.convocatoria WHERE r.proyecto.id = :proyectoId ORDER BY r.puntuacion DESC")
    List<Recomendacion> findByProyectoIdOrderByPuntuacionDesc(@Param("proyectoId") Long proyectoId);

    /**
     * Elimina todas las recomendaciones de un proyecto (para regenerarlas).
     */
    @Modifying
    @Query("DELETE FROM Recomendacion r WHERE r.proyecto.id = :proyectoId")
    void deleteByProyectoId(@Param("proyectoId") Long proyectoId);

    /**
     * Elimina todas las recomendaciones asociadas a los proyectos de un usuario.
     * Se usa para eliminación administrativa de usuario sin violar FK.
     */
    @Modifying
    @Query("DELETE FROM Recomendacion r WHERE r.proyecto.usuario.id = :usuarioId")
    void deleteByProyectoUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Cuenta las recomendaciones de un proyecto.
     */
    long countByProyectoId(Long proyectoId);

    /**
     * Cuenta el total de recomendaciones del sistema en una sola query.
     * Evita el patron N+1 que itere sobre proyectos.
     */
    @Query("SELECT COUNT(r) FROM Recomendacion r")
    long countAll();

    /**
     * Filtra recomendaciones de un proyecto por tipo, sector y ubicacion delegando a BD.
     * - tipo y sector: coincidencia exacta, ignorados si son null
     * - ubicacion: búsqueda parcial LIKE, ignorada si es null
     */
    @Query("SELECT r FROM Recomendacion r JOIN FETCH r.convocatoria c " +
            "WHERE r.proyecto.id = :proyectoId " +
            "AND (:tipo      IS NULL OR c.tipo      = :tipo) " +
            "AND (:sector    IS NULL OR c.sector    = :sector) " +
            "AND (:ubicacion = '' OR LOWER(c.ubicacion) LIKE LOWER(CONCAT('%', :ubicacion, '%'))) " +
            "ORDER BY r.puntuacion DESC")
    List<Recomendacion> filtrar(@Param("proyectoId") Long proyectoId,
                                @Param("tipo")       String tipo,
                                @Param("sector")     String sector,
                                @Param("ubicacion")  String ubicacion);

    @Query("SELECT r FROM Recomendacion r JOIN FETCH r.convocatoria c " +
            "WHERE r.proyecto.id = :proyectoId " +
            "AND (:tipo      IS NULL OR c.tipo      = :tipo) " +
            "AND (:sector    IS NULL OR c.sector    = :sector) " +
            "AND (:filtrarRegion = false OR (c.regionId IN :regionIds OR c.provinciaId IN :regionIds) OR LOWER(c.ubicacion) = 'nacional' OR LOWER(c.ubicacion) LIKE LOWER(CONCAT('%', :ubicacionTexto, '%'))) " +
            "ORDER BY r.puntuacion DESC")
    List<Recomendacion> filtrarConRegion(@Param("proyectoId") Long proyectoId,
                                         @Param("tipo")       String tipo,
                                         @Param("sector")     String sector,
                                         @Param("filtrarRegion") boolean filtrarRegion,
                                         @Param("regionIds") Collection<Integer> regionIds,
                                         @Param("ubicacionTexto") String ubicacionTexto);

    /**
     * Obtiene los tipos de convocatoria distintos para las recomendaciones de un proyecto.
     * Usado para poblar el selector de filtros en la vista.
     */
    @Query("SELECT DISTINCT c.tipo FROM Recomendacion r JOIN r.convocatoria c " +
            "WHERE r.proyecto.id = :proyectoId AND c.tipo IS NOT NULL ORDER BY c.tipo")
    List<String> findTiposDistintosByProyectoId(@Param("proyectoId") Long proyectoId);

    /**
     * Obtiene los sectores distintos para las recomendaciones de un proyecto.
     * Usado para poblar el selector de filtros en la vista.
     */
    @Query("SELECT DISTINCT c.sector FROM Recomendacion r JOIN r.convocatoria c " +
            "WHERE r.proyecto.id = :proyectoId AND c.sector IS NOT NULL ORDER BY c.sector")
    List<String> findSectoresDistintosByProyectoId(@Param("proyectoId") Long proyectoId);

    /**
     * Obtiene una recomendación por ID verificando que pertenece al proyecto indicado.
     * Usa JOIN FETCH para cargar la convocatoria (evita LazyInitializationException).
     */
    @Query("SELECT r FROM Recomendacion r JOIN FETCH r.convocatoria WHERE r.id = :id AND r.proyecto.id = :proyectoId")
    Optional<Recomendacion> findByIdAndProyectoId(@Param("id") Long id, @Param("proyectoId") Long proyectoId);

    /**
     * Actualiza el campo guiaEnriquecida de una recomendación existente.
     * Usado para persistir la guía JSON generada bajo demanda por OpenAiGuiaService.
     */
    @Modifying
    @Query("UPDATE Recomendacion r SET r.guiaEnriquecida = :guiaJson WHERE r.id = :id")
    void actualizarGuiaEnriquecida(@Param("id") Long id, @Param("guiaJson") String guiaJson);

    /**
     * Invalida todas las guías enriquecidas cacheadas.
     * Usado al actualizar el formato/prompt de generación de guías para forzar regeneración.
     */
    @Modifying
    @Query("UPDATE Recomendacion r SET r.guiaEnriquecida = null WHERE r.guiaEnriquecida IS NOT NULL")
    int invalidarTodasLasGuiasEnriquecidas();

    /**
     * Elimina las recomendaciones NO evaluadas por IA de un proyecto.
     * Permite regenerar candidatas de búsqueda rápida sin perder las analizadas por IA.
     */
    @Modifying
    @Query("DELETE FROM Recomendacion r WHERE r.proyecto.id = :proyectoId AND r.usadaIa = false")
    void deleteByProyectoIdAndUsadaIaFalse(@Param("proyectoId") Long proyectoId);

    /**
     * Obtiene todas las recomendaciones de un proyecto (con JOIN FETCH en convocatoria).
     */
    @Query("SELECT r FROM Recomendacion r JOIN FETCH r.convocatoria WHERE r.proyecto.id = :proyectoId")
    List<Recomendacion> findByProyectoId(@Param("proyectoId") Long proyectoId);

    /**
     * Obtiene las recomendaciones NO evaluadas por IA de un proyecto (candidatas de búsqueda rápida).
     * Usada por MotorMatchingService para analizar con IA las candidatas existentes en BD.
     */
    @Query("SELECT r FROM Recomendacion r JOIN FETCH r.convocatoria WHERE r.proyecto.id = :proyectoId AND r.usadaIa = false")
    List<Recomendacion> findByProyectoIdAndUsadaIaFalse(@Param("proyectoId") Long proyectoId);
}
