package com.syntia.ai.repository;

import com.syntia.ai.model.ConvocatoriaFavorita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface ConvocatoriaFavoritaRepository extends JpaRepository<ConvocatoriaFavorita, Long> {

    boolean existsByUsuarioIdAndConvocatoriaId(Long usuarioId, Long convocatoriaId);

    long deleteByUsuarioIdAndConvocatoriaId(Long usuarioId, Long convocatoriaId);

    long deleteByUsuarioId(Long usuarioId);

    @Query("SELECT f FROM ConvocatoriaFavorita f JOIN FETCH f.convocatoria WHERE f.usuario.id = :usuarioId ORDER BY f.creadaEn DESC")
    List<ConvocatoriaFavorita> findByUsuarioIdConConvocatoria(@Param("usuarioId") Long usuarioId);

    @Query("SELECT f.convocatoria.id FROM ConvocatoriaFavorita f WHERE f.usuario.id = :usuarioId")
    Set<Long> findConvocatoriaIdsByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT f.convocatoria.id FROM ConvocatoriaFavorita f WHERE f.usuario.id = :usuarioId AND f.convocatoria.id IN :convocatoriaIds")
    Set<Long> findConvocatoriaIdsByUsuarioIdAndConvocatoriaIdIn(@Param("usuarioId") Long usuarioId,
                                                                @Param("convocatoriaIds") Collection<Long> convocatoriaIds);
}

