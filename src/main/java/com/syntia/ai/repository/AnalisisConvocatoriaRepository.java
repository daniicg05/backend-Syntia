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
}
