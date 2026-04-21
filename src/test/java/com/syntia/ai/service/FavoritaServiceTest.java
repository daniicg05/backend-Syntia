package com.syntia.ai.service;

import com.syntia.ai.model.EstadoSolicitudFavorita;
import com.syntia.ai.model.UserFavoriteConvocatoria;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.FavoritaImportResultDTO;
import com.syntia.ai.model.dto.FavoritaItemImportDTO;
import com.syntia.ai.model.dto.FavoritaUpsertRequestDTO;
import com.syntia.ai.repository.UserFavoriteConvocatoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoritaServiceTest {

    @Mock
    private UserFavoriteConvocatoriaRepository favoritaRepository;
    @Mock
    private UsuarioService usuarioService;

    private FavoritaService favoritaService;

    @BeforeEach
    void setUp() {
        favoritaService = new FavoritaService(favoritaRepository, usuarioService);
    }

    @Test
    void upsert_creaFavoritaSiNoExiste() {
        FavoritaUpsertRequestDTO req = new FavoritaUpsertRequestDTO();
        req.setConvocatoriaId(12L);
        req.setTitulo("Ayudas X");

        Usuario usuario = Usuario.builder().id(1L).build();
        when(usuarioService.obtenerPorId(1L)).thenReturn(usuario);
        when(favoritaRepository.findByUsuarioIdAndConvocatoriaId(1L, 12L)).thenReturn(Optional.empty());
        when(favoritaRepository.save(any(UserFavoriteConvocatoria.class))).thenAnswer(i -> i.getArgument(0));

        FavoritaService.UpsertResult result = favoritaService.upsert(1L, req);

        assertTrue(result.created());
        assertEquals(12L, result.favorita().getId());
        assertEquals(EstadoSolicitudFavorita.NO_SOLICITADA, result.favorita().getEstadoSolicitud());
    }

    @Test
    void actualizarEstado_modificaEstadoSolicitud() {
        UserFavoriteConvocatoria favorita = UserFavoriteConvocatoria.builder()
                .id(7L)
                .convocatoriaId(12L)
                .titulo("Ayudas X")
                .estadoSolicitud(EstadoSolicitudFavorita.NO_SOLICITADA)
                .build();

        when(favoritaRepository.findByUsuarioIdAndConvocatoriaId(1L, 12L)).thenReturn(Optional.of(favorita));
        when(favoritaRepository.save(any(UserFavoriteConvocatoria.class))).thenAnswer(i -> i.getArgument(0));

        var dto = favoritaService.actualizarEstado(1L, 12L, EstadoSolicitudFavorita.SOLICITADA);

        assertEquals(EstadoSolicitudFavorita.SOLICITADA, dto.getEstadoSolicitud());
    }

    @Test
    void importar_deduplicaYCuentaImportadasActualizadasYOmitidas() {
        Usuario usuario = Usuario.builder().id(1L).build();
        when(usuarioService.obtenerPorId(1L)).thenReturn(usuario);

        FavoritaItemImportDTO nueva = new FavoritaItemImportDTO();
        nueva.setConvocatoriaId(100L);
        nueva.setTitulo("Nueva");
        nueva.setGuardadaEn(Instant.parse("2026-04-21T10:00:00Z"));

        FavoritaItemImportDTO existente = new FavoritaItemImportDTO();
        existente.setConvocatoriaId(200L);
        existente.setTitulo("Existente");
        existente.setEstadoSolicitud(EstadoSolicitudFavorita.SOLICITADA);

        FavoritaItemImportDTO invalida = new FavoritaItemImportDTO();
        invalida.setConvocatoriaId(-1L);

        when(favoritaRepository.findByUsuarioIdAndConvocatoriaId(1L, 100L)).thenReturn(Optional.empty());
        when(favoritaRepository.findByUsuarioIdAndConvocatoriaId(1L, 200L)).thenReturn(Optional.of(
                UserFavoriteConvocatoria.builder().convocatoriaId(200L).titulo("x").build()
        ));
        when(favoritaRepository.save(any(UserFavoriteConvocatoria.class))).thenAnswer(i -> i.getArgument(0));

        FavoritaImportResultDTO result = favoritaService.importar(1L, List.of(nueva, existente, invalida));

        assertEquals(1, result.getImportadas());
        assertEquals(1, result.getActualizadas());
        assertEquals(1, result.getOmitidas());

        ArgumentCaptor<UserFavoriteConvocatoria> captor = ArgumentCaptor.forClass(UserFavoriteConvocatoria.class);
        verify(favoritaRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
    }
}

