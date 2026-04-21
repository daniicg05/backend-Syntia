package com.syntia.ai.repository;

import com.syntia.ai.model.EstadoSolicitudFavorita;
import com.syntia.ai.model.UserFavoriteConvocatoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserFavoriteConvocatoriaRepository extends JpaRepository<UserFavoriteConvocatoria, Long> {

    Optional<UserFavoriteConvocatoria> findByUsuarioIdAndConvocatoriaId(Long usuarioId, Long convocatoriaId);

    @Query("SELECT f FROM UserFavoriteConvocatoria f " +
            "WHERE f.usuario.id = :usuarioId " +
            "AND (:estado IS NULL OR f.estadoSolicitud = :estado) " +
            "AND (:q IS NULL OR " +
            "LOWER(f.titulo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(f.organismo, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(f.sector, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(COALESCE(f.ubicacion, '')) LIKE LOWER(CONCAT('%', :q, '%')))" )
    Page<UserFavoriteConvocatoria> buscarPorUsuario(@Param("usuarioId") Long usuarioId,
                                                    @Param("estado") EstadoSolicitudFavorita estado,
                                                    @Param("q") String q,
                                                    Pageable pageable);

    int deleteByUsuarioIdAndConvocatoriaId(Long usuarioId, Long convocatoriaId);

    void deleteByUsuarioId(Long usuarioId);
}

