package com.syntia.ai.repository;

import com.syntia.ai.model.Perfil;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PerfilRepository {
    Optional<Perfil> findByUsuarioId(Long usuarioId);
}
