package com.syntia.ai.repository;

import com.syntia.ai.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
    List<Proyecto> findByUsuarioId(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);

    @Query("SELECT p FROM Proyecto p JOIN FETCH p.usuario WHERE p.id = :id")
    Optional<Proyecto> findByIdWithUsuario(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Proyecto p")
    long countAll();
}
