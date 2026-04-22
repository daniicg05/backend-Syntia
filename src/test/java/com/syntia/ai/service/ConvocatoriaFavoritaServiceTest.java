package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.repository.ConvocatoriaFavoritaRepository;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvocatoriaFavoritaServiceTest {

    @Mock
    private ConvocatoriaFavoritaRepository favoritaRepository;
    @Mock
    private ConvocatoriaRepository convocatoriaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private ConvocatoriaFavoritaService service;

    @BeforeEach
    void setUp() {
        service = new ConvocatoriaFavoritaService(favoritaRepository, convocatoriaRepository, usuarioRepository);
    }

    @Test
    void marcarFavorita_guardaRelacion_siNoExiste() {
        Usuario usuario = Usuario.builder().id(7L).email("u@demo.com").build();
        Convocatoria convocatoria = Convocatoria.builder().id(33L).titulo("Ayuda demo").build();

        when(favoritaRepository.existsByUsuarioIdAndConvocatoriaId(7L, 33L)).thenReturn(false);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(convocatoriaRepository.findById(33L)).thenReturn(Optional.of(convocatoria));

        service.marcarFavorita(7L, 33L);

        ArgumentCaptor<com.syntia.ai.model.ConvocatoriaFavorita> captor =
                ArgumentCaptor.forClass(com.syntia.ai.model.ConvocatoriaFavorita.class);
        verify(favoritaRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuario().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getConvocatoria().getId()).isEqualTo(33L);
    }

    @Test
    void marcarFavorita_noGuardaDuplicado_siYaExiste() {
        when(favoritaRepository.existsByUsuarioIdAndConvocatoriaId(7L, 33L)).thenReturn(true);

        service.marcarFavorita(7L, 33L);

        verify(favoritaRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(usuarioRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(convocatoriaRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void obtenerIdsFavoritasEn_retornaVacio_siListaVacia() {
        Set<Long> resultado = service.obtenerIdsFavoritasEn(7L, List.of());

        assertThat(resultado).isEmpty();
        verify(favoritaRepository, never())
                .findConvocatoriaIdsByUsuarioIdAndConvocatoriaIdIn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void desmarcarFavorita_eliminaRelacion() {
        service.desmarcarFavorita(7L, 33L);

        verify(favoritaRepository).deleteByUsuarioIdAndConvocatoriaId(7L, 33L);
    }
}

