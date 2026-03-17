package com.syntia.ai.service;

import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.PerfilDTO;
import com.syntia.ai.repository.PerfilRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Servicio de lógica de negocio para la gestión del perfil de usuario.
 * <p>
 * Decisión arquitectónica: el servicio recibe el objeto {@link Usuario} completo
 * (ya resuelto en el controller) en lugar del ID, para evitar una segunda consulta
 * a la BD y mantener la capa de servicio agnóstica del contexto de seguridad.
 */
@Service
public class PerfilService {

    /** Repositorio de acceso a datos de perfil. */
    private final PerfilRepository perfilRepository;

    /** Inyección por constructor para garantizar dependencia obligatoria e inmutabilidad. */
    public PerfilService(PerfilRepository perfilRepository) {
        this.perfilRepository = perfilRepository;
    }

    /**
     * Indica si un usuario ya tiene perfil creado.
     *
     * @param usuarioId ID del usuario
     * @return true si el perfil existe
     */
    public boolean tienePerfil(Long usuarioId) {
        return perfilRepository.findByUsuarioId(usuarioId).isPresent();
    }

    /**
     * Obtiene el perfil de un usuario. Devuelve Optional vacío si aún no lo ha creado.
     *
     * @param usuarioId ID del usuario
     * @return Optional con el perfil
     */
    public Optional<Perfil> obtenerPerfil(Long usuarioId) {
        return perfilRepository.findByUsuarioId(usuarioId);
    }

    /**
     * Crea un nuevo perfil para el usuario.
     * <p>
     * Se usa cuando el usuario accede por primera vez al formulario de perfil.
     *
     * @param usuario usuario autenticado
     * @param dto     datos del formulario
     * @return perfil guardado
     * @throws IllegalStateException si el usuario ya tiene un perfil
     */
    @Transactional
    public Perfil crearPerfil(Usuario usuario, PerfilDTO dto) {

        /** Regla de negocio: no permitir más de un perfil por usuario. */
        if (tienePerfil(usuario.getId())) {
            throw new IllegalStateException("El usuario ya tiene un perfil. Usa actualizar.");
        }

        /** Construcción de la entidad a partir de los datos recibidos. */
        Perfil perfil = Perfil.builder()
                .usuario(usuario)
                .sector(dto.getSector())
                .ubicacion(dto.getUbicacion())
                .tipoEntidad(dto.getTipoEntidad())
                .objetivos(dto.getObjetivos())
                .necesidadesFinanciacion(dto.getNecesidadesFinanciacion())
                .descripcionLibre(dto.getDescripcionLibre())
                .build();

        /** Persistencia del nuevo perfil. */
        return perfilRepository.save(perfil);
    }

    /**
     * Actualiza el perfil existente de un usuario.
     *
     * @param usuarioId ID del usuario
     * @param dto       datos del formulario
     * @return perfil actualizado
     * @throws EntityNotFoundException si el usuario no tiene perfil
     */
    @Transactional
    public Perfil actualizarPerfil(Long usuarioId, PerfilDTO dto) {

        /** Se recupera el perfil actual o se lanza excepción si no existe. */
        Perfil perfil = perfilRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Perfil no encontrado para el usuario: " + usuarioId));

        /** Se aplican cambios campo por campo para mantener control explícito de actualización. */
        perfil.setSector(dto.getSector());
        perfil.setUbicacion(dto.getUbicacion());
        perfil.setTipoEntidad(dto.getTipoEntidad());
        perfil.setObjetivos(dto.getObjetivos());
        perfil.setNecesidadesFinanciacion(dto.getNecesidadesFinanciacion());
        perfil.setDescripcionLibre(dto.getDescripcionLibre());

        /** Guardado de la entidad actualizada. */
        return perfilRepository.save(perfil);
    }

    /**
     * Convierte un {@link Perfil} en su {@link PerfilDTO} equivalente.
     * Útil para precargar el formulario de edición.
     *
     * @param perfil entidad
     * @return DTO con los datos del perfil
     */
    public PerfilDTO toDTO(Perfil perfil) {

        /** Mapeo manual de entidad a DTO para exponer solo los datos necesarios. */
        PerfilDTO dto = new PerfilDTO();
        dto.setSector(perfil.getSector());
        dto.setUbicacion(perfil.getUbicacion());
        dto.setTipoEntidad(perfil.getTipoEntidad());
        dto.setObjetivos(perfil.getObjetivos());
        dto.setNecesidadesFinanciacion(perfil.getNecesidadesFinanciacion());
        dto.setDescripcionLibre(perfil.getDescripcionLibre());
        return dto;
    }
}