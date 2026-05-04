package com.syntia.ai.repository;

import com.syntia.ai.model.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FavoritoRepository extends JpaRepository<Favorito, Long> {

    @Query("SELECT f FROM Favorito f JOIN FETCH f.convocatoria WHERE f.usuario.id = :usuarioId ORDER BY f.agregadoEn DESC")
    List<Favorito> findByUsuarioIdOrderByAgregadoEnDesc(Long usuarioId);

    Optional<Favorito> findByUsuarioIdAndConvocatoriaId(Long usuarioId, Long convocatoriaId);

    void deleteByUsuarioIdAndConvocatoriaId(Long usuarioId, Long convocatoriaId);

    @Query("SELECT f.convocatoria.id FROM Favorito f WHERE f.usuario.id = :usuarioId")
    Set<Long> findConvocatoriaIdsByUsuarioId(Long usuarioId);

    long countByUsuarioId(Long usuarioId);
}
