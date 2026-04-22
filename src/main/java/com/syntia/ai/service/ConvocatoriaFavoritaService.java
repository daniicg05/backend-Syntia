package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.ConvocatoriaFavorita;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.ConvocatoriaFavoritaDTO;
import com.syntia.ai.repository.ConvocatoriaFavoritaRepository;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ConvocatoriaFavoritaService {

    private final ConvocatoriaFavoritaRepository favoritaRepository;
    private final ConvocatoriaRepository convocatoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public ConvocatoriaFavoritaService(ConvocatoriaFavoritaRepository favoritaRepository,
                                       ConvocatoriaRepository convocatoriaRepository,
                                       UsuarioRepository usuarioRepository) {
        this.favoritaRepository = favoritaRepository;
        this.convocatoriaRepository = convocatoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void marcarFavorita(Long usuarioId, Long convocatoriaId) {
        if (favoritaRepository.existsByUsuarioIdAndConvocatoriaId(usuarioId, convocatoriaId)) {
            return;
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + usuarioId));

        Convocatoria convocatoria = convocatoriaRepository.findById(convocatoriaId)
                .orElseThrow(() -> new EntityNotFoundException("Convocatoria no encontrada: " + convocatoriaId));

        ConvocatoriaFavorita favorita = ConvocatoriaFavorita.builder()
                .usuario(usuario)
                .convocatoria(convocatoria)
                .build();

        favoritaRepository.save(favorita);
    }

    @Transactional
    public void desmarcarFavorita(Long usuarioId, Long convocatoriaId) {
        favoritaRepository.deleteByUsuarioIdAndConvocatoriaId(usuarioId, convocatoriaId);
    }

    public boolean esFavorita(Long usuarioId, Long convocatoriaId) {
        return favoritaRepository.existsByUsuarioIdAndConvocatoriaId(usuarioId, convocatoriaId);
    }

    public Set<Long> listarIdsFavoritas(Long usuarioId) {
        return favoritaRepository.findConvocatoriaIdsByUsuarioId(usuarioId);
    }

    public Set<Long> obtenerIdsFavoritasEn(Long usuarioId, Collection<Long> convocatoriaIds) {
        if (convocatoriaIds == null || convocatoriaIds.isEmpty()) {
            return Set.of();
        }
        return favoritaRepository.findConvocatoriaIdsByUsuarioIdAndConvocatoriaIdIn(usuarioId, convocatoriaIds);
    }

    public List<ConvocatoriaFavoritaDTO> listarFavoritas(Long usuarioId) {
        return favoritaRepository.findByUsuarioIdConConvocatoria(usuarioId).stream()
                .map(this::toDTO)
                .toList();
    }

    private ConvocatoriaFavoritaDTO toDTO(ConvocatoriaFavorita favorita) {
        ConvocatoriaFavoritaDTO dto = new ConvocatoriaFavoritaDTO();
        Convocatoria convocatoria = favorita.getConvocatoria();

        dto.setConvocatoriaId(convocatoria.getId());
        dto.setTitulo(convocatoria.getTitulo());
        dto.setTipo(convocatoria.getTipo());
        dto.setSector(convocatoria.getSector());
        dto.setUbicacion(convocatoria.getUbicacion());
        dto.setUrlOficial(convocatoria.getUrlOficial());
        dto.setFuente(convocatoria.getFuente());
        dto.setFechaCierre(convocatoria.getFechaCierre());
        dto.setOrganismo(convocatoria.getOrganismo());
        dto.setPresupuesto(convocatoria.getPresupuesto());
        dto.setFechaPublicacion(convocatoria.getFechaPublicacion());
        dto.setFechaFavorita(favorita.getCreadaEn());

        return dto;
    }
}

