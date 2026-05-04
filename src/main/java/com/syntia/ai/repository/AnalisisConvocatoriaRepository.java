package com.syntia.ai.repository;

import com.syntia.ai.model.AnalisisConvocatoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalisisConvocatoriaRepository extends JpaRepository<AnalisisConvocatoria, Long> {

    Optional<AnalisisConvocatoria> findByConvocatoriaIdAndUsuarioId(Long convocatoriaId, Long usuarioId);

    @Query("SELECT a FROM AnalisisConvocatoria a JOIN FETCH a.convocatoria LEFT JOIN FETCH a.proyecto " +
            "WHERE a.usuario.id = :usuarioId AND a.guiaEnriquecida IS NOT NULL " +
            "ORDER BY a.creadoEn DESC")
    List<AnalisisConvocatoria> findGuiasEnriquecidasByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("DELETE FROM AnalisisConvocatoria a WHERE a.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("UPDATE AnalisisConvocatoria a SET a.guiaEnriquecida = null WHERE a.id = :id AND a.usuario.id = :usuarioId")
    int limpiarGuiaEnriquecidaByIdAndUsuarioId(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("UPDATE AnalisisConvocatoria a SET a.guiaEnriquecida = null, a.proyecto = null WHERE a.proyecto.id = :proyectoId")
    int limpiarGuiaYProyectoByProyectoId(@Param("proyectoId") Long proyectoId);

    @Modifying
    @Query("DELETE FROM AnalisisConvocatoria a WHERE a.id = :id AND a.usuario.id = :usuarioId")
    int deleteByIdAndUsuarioId(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("DELETE FROM AnalisisConvocatoria a WHERE a.proyecto.id = :proyectoId")
    void deleteByProyectoId(@Param("proyectoId") Long proyectoId);
}
