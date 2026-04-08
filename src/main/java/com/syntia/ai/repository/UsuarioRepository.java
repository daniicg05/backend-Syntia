package com.syntia.ai.repository;

import com.syntia.ai.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Busca el usuario asociado a un token de verificación de cuenta. */
    Optional<Usuario> findByVerificationToken(String token);
}
