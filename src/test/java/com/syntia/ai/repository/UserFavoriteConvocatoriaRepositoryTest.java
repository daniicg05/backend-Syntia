package com.syntia.ai.repository;

import com.syntia.ai.model.EstadoSolicitudFavorita;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.UserFavoriteConvocatoria;
import com.syntia.ai.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class UserFavoriteConvocatoriaRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UserFavoriteConvocatoriaRepository favoritaRepository;

    @Test
    void usuarioANoVeFavoritasDeUsuarioB() {
        Usuario userA = usuarioRepository.save(Usuario.builder()
                .email("a@test.com")
                .password("hash")
                .rol(Rol.USUARIO)
                .build());

        Usuario userB = usuarioRepository.save(Usuario.builder()
                .email("b@test.com")
                .password("hash")
                .rol(Rol.USUARIO)
                .build());

        favoritaRepository.save(UserFavoriteConvocatoria.builder()
                .usuario(userA)
                .convocatoriaId(10L)
                .titulo("Fav A")
                .estadoSolicitud(EstadoSolicitudFavorita.NO_SOLICITADA)
                .build());

        favoritaRepository.save(UserFavoriteConvocatoria.builder()
                .usuario(userB)
                .convocatoriaId(20L)
                .titulo("Fav B")
                .estadoSolicitud(EstadoSolicitudFavorita.SOLICITADA)
                .build());

        var pageA = favoritaRepository.buscarPorUsuario(userA.getId(), null, null, PageRequest.of(0, 10));
        assertEquals(1, pageA.getTotalElements());
        assertEquals(10L, pageA.getContent().get(0).getConvocatoriaId());

        var filtradasA = favoritaRepository.buscarPorUsuario(
                userA.getId(),
                EstadoSolicitudFavorita.NO_SOLICITADA,
                "Fav",
                PageRequest.of(0, 10)
        );
        assertEquals(1, filtradasA.getTotalElements());
    }
}

