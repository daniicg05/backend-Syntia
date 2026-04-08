package com.syntia.ai.repository;

import com.syntia.ai.model.HistorialCorreo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialCorreoRepository extends JpaRepository<HistorialCorreo, Long> {
    List<HistorialCorreo> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);
}

