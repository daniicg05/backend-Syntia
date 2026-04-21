package com.syntia.ai.service;

import com.syntia.ai.model.EstadoSolicitudFavorita;
import com.syntia.ai.model.UserFavoriteConvocatoria;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.FavoritaImportResultDTO;
import com.syntia.ai.model.dto.FavoritaItemImportDTO;
import com.syntia.ai.model.dto.FavoritaResponseDTO;
import com.syntia.ai.model.dto.FavoritaUpsertRequestDTO;
import com.syntia.ai.repository.UserFavoriteConvocatoriaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class FavoritaService {

    private final UserFavoriteConvocatoriaRepository favoritaRepository;
    private final UsuarioService usuarioService;

    public FavoritaService(UserFavoriteConvocatoriaRepository favoritaRepository,
                           UsuarioService usuarioService) {
        this.favoritaRepository = favoritaRepository;
        this.usuarioService = usuarioService;
    }

    @Transactional(readOnly = true)
    public Page<FavoritaResponseDTO> listar(Long usuarioId,
                                            EstadoSolicitudFavorita estadoSolicitud,
                                            String q,
                                            Pageable pageable) {
        String query = sanitizeText(q);
        return favoritaRepository.buscarPorUsuario(usuarioId, estadoSolicitud, query, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public FavoritaResponseDTO obtenerPorConvocatoria(Long usuarioId, Long convocatoriaId) {
        return favoritaRepository.findByUsuarioIdAndConvocatoriaId(usuarioId, convocatoriaId)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Favorita no encontrada para convocatoria " + convocatoriaId));
    }

    @Transactional
    public UpsertResult upsert(Long usuarioId, FavoritaUpsertRequestDTO request) {
        Usuario usuario = usuarioService.obtenerPorId(usuarioId);

        Optional<UserFavoriteConvocatoria> existenteOpt = favoritaRepository
                .findByUsuarioIdAndConvocatoriaId(usuarioId, request.getConvocatoriaId());

        UserFavoriteConvocatoria favorita = existenteOpt.orElseGet(() -> UserFavoriteConvocatoria.builder()
                .usuario(usuario)
                .convocatoriaId(request.getConvocatoriaId())
                .estadoSolicitud(EstadoSolicitudFavorita.NO_SOLICITADA)
                .build());

        applySnapshot(favorita, request);
        UserFavoriteConvocatoria guardada = favoritaRepository.save(favorita);

        log.info("favorita_upsert usuarioId={} convocatoriaId={} creada={}",
                usuarioId, request.getConvocatoriaId(), existenteOpt.isEmpty());

        return new UpsertResult(toDto(guardada), existenteOpt.isEmpty());
    }

    @Transactional
    public void eliminar(Long usuarioId, Long convocatoriaId) {
        int eliminadas = favoritaRepository.deleteByUsuarioIdAndConvocatoriaId(usuarioId, convocatoriaId);
        if (eliminadas == 0) {
            throw new EntityNotFoundException("Favorita no encontrada para convocatoria " + convocatoriaId);
        }
        log.info("favorita_delete usuarioId={} convocatoriaId={}", usuarioId, convocatoriaId);
    }

    @Transactional
    public FavoritaResponseDTO actualizarEstado(Long usuarioId,
                                                Long convocatoriaId,
                                                EstadoSolicitudFavorita estadoSolicitud) {
        UserFavoriteConvocatoria favorita = favoritaRepository
                .findByUsuarioIdAndConvocatoriaId(usuarioId, convocatoriaId)
                .orElseThrow(() -> new EntityNotFoundException("Favorita no encontrada para convocatoria " + convocatoriaId));

        favorita.setEstadoSolicitud(estadoSolicitud);
        UserFavoriteConvocatoria guardada = favoritaRepository.save(favorita);
        log.info("favorita_estado usuarioId={} convocatoriaId={} estado={}", usuarioId, convocatoriaId, estadoSolicitud.getValue());
        return toDto(guardada);
    }

    @Transactional
    public FavoritaImportResultDTO importar(Long usuarioId, List<FavoritaItemImportDTO> favoritasImport) {
        Usuario usuario = usuarioService.obtenerPorId(usuarioId);

        int importadas = 0;
        int actualizadas = 0;
        int omitidas = 0;

        for (FavoritaItemImportDTO item : favoritasImport) {
            if (item.getConvocatoriaId() == null || item.getConvocatoriaId() <= 0 || item.getTitulo() == null || item.getTitulo().isBlank()) {
                omitidas++;
                continue;
            }

            Optional<UserFavoriteConvocatoria> existenteOpt = favoritaRepository
                    .findByUsuarioIdAndConvocatoriaId(usuarioId, item.getConvocatoriaId());

            UserFavoriteConvocatoria favorita = existenteOpt.orElseGet(() -> UserFavoriteConvocatoria.builder()
                    .usuario(usuario)
                    .convocatoriaId(item.getConvocatoriaId())
                    .estadoSolicitud(EstadoSolicitudFavorita.NO_SOLICITADA)
                    .build());

            favorita.setTitulo(trimRequired(item.getTitulo()));
            favorita.setOrganismo(sanitizeText(item.getOrganismo()));
            favorita.setUbicacion(sanitizeText(item.getUbicacion()));
            favorita.setTipo(sanitizeText(item.getTipo()));
            favorita.setSector(sanitizeText(item.getSector()));
            favorita.setFechaPublicacion(item.getFechaPublicacion());
            favorita.setFechaCierre(item.getFechaCierre());
            favorita.setPresupuesto(item.getPresupuesto());
            favorita.setAbierto(item.getAbierto());
            favorita.setUrlOficial(normalizeUrl(item.getUrlOficial(), item.getIdBdns()));
            favorita.setIdBdns(sanitizeText(item.getIdBdns()));
            favorita.setNumeroConvocatoria(sanitizeText(item.getNumeroConvocatoria()));
            if (item.getEstadoSolicitud() != null) {
                favorita.setEstadoSolicitud(item.getEstadoSolicitud());
            }
            if (item.getGuardadaEn() != null) {
                favorita.setGuardadaEn(LocalDateTime.ofInstant(item.getGuardadaEn(), ZoneOffset.UTC));
            }

            favoritaRepository.save(favorita);
            if (existenteOpt.isPresent()) {
                actualizadas++;
            } else {
                importadas++;
            }
        }

        log.info("favorita_import usuarioId={} importadas={} actualizadas={} omitidas={}",
                usuarioId, importadas, actualizadas, omitidas);

        return new FavoritaImportResultDTO(importadas, actualizadas, omitidas);
    }

    public Long resolverUsuarioId(String email) {
        return usuarioService.buscarPorEmail(email)
                .map(Usuario::getId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + email));
    }

    private void applySnapshot(UserFavoriteConvocatoria favorita, FavoritaUpsertRequestDTO request) {
        favorita.setTitulo(trimRequired(request.getTitulo()));
        favorita.setOrganismo(sanitizeText(request.getOrganismo()));
        favorita.setUbicacion(sanitizeText(request.getUbicacion()));
        favorita.setTipo(sanitizeText(request.getTipo()));
        favorita.setSector(sanitizeText(request.getSector()));
        favorita.setFechaPublicacion(request.getFechaPublicacion());
        favorita.setFechaCierre(request.getFechaCierre());
        favorita.setPresupuesto(request.getPresupuesto());
        favorita.setAbierto(request.getAbierto());
        favorita.setUrlOficial(normalizeUrl(request.getUrlOficial(), request.getIdBdns()));
        favorita.setIdBdns(sanitizeText(request.getIdBdns()));
        favorita.setNumeroConvocatoria(sanitizeText(request.getNumeroConvocatoria()));
    }

    private FavoritaResponseDTO toDto(UserFavoriteConvocatoria favorita) {
        return FavoritaResponseDTO.builder()
                .id(favorita.getConvocatoriaId())
                .titulo(favorita.getTitulo())
                .organismo(favorita.getOrganismo())
                .ubicacion(favorita.getUbicacion())
                .tipo(favorita.getTipo())
                .sector(favorita.getSector())
                .fechaPublicacion(favorita.getFechaPublicacion())
                .fechaCierre(favorita.getFechaCierre())
                .presupuesto(favorita.getPresupuesto())
                .abierto(favorita.getAbierto())
                .urlOficial(favorita.getUrlOficial())
                .idBdns(favorita.getIdBdns())
                .numeroConvocatoria(favorita.getNumeroConvocatoria())
                .estadoSolicitud(favorita.getEstadoSolicitud())
                .guardadaEn(favorita.getGuardadaEn())
                .build();
    }

    private String normalizeUrl(String urlOficial, String idBdns) {
        String sanitizedUrl = sanitizeText(urlOficial);
        if (sanitizedUrl != null) {
            try {
                URI.create(sanitizedUrl);
            } catch (Exception ex) {
                throw new IllegalArgumentException("urlOficial invalida");
            }
            return sanitizedUrl;
        }

        String idBdnsSanitized = sanitizeText(idBdns);
        if (idBdnsSanitized != null) {
            return "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + idBdnsSanitized;
        }
        return null;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("titulo es obligatorio");
        }
        return value.trim();
    }

    public record UpsertResult(FavoritaResponseDTO favorita, boolean created) {
    }
}

