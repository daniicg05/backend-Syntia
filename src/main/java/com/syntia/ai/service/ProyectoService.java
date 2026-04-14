package com.syntia.ai.service;

import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.ProyectoDTO;
import com.syntia.ai.repository.ProyectoRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de lógica de negocio para la gestión de proyectos.
 * <p>
 * Decisión arquitectónica: la verificación de propiedad (el proyecto pertenece
 * al usuario autenticado) se realiza en el servicio y no en el controller,
 * siguiendo el principio de que las reglas de negocio viven en la capa de servicio.
 */
@Service
public class ProyectoService {

    /** Repositorio para acceso y persistencia de proyectos. */
    private final ProyectoRepository proyectoRepository;
    private final RecomendacionRepository recomendacionRepository;

    public ProyectoService(ProyectoRepository proyectoRepository,
                           RecomendacionRepository recomendacionRepository) {
        this.proyectoRepository = proyectoRepository;
        this.recomendacionRepository = recomendacionRepository;
    }

    /**
     * Obtiene todos los proyectos del usuario autenticado.
     *
     * @param usuarioId ID del usuario
     * @return lista de proyectos del usuario
     */
    public List<Proyecto> obtenerProyectos(Long usuarioId) {

        /** Filtra proyectos por propietario para mantener aislamiento de datos. */
        return proyectoRepository.findByUsuarioId(usuarioId);
    }

    /**
     * Obtiene un proyecto por ID verificando que pertenece al usuario.
     * Usa JOIN FETCH para cargar el usuario en la misma query y evitar
     * LazyInitializationException al acceder a proyecto.getUsuario().
     *
     * @param id        ID del proyecto
     * @param usuarioId ID del usuario autenticado
     * @return el proyecto si existe y pertenece al usuario
     * @throws EntityNotFoundException si el proyecto no existe
     * @throws AccessDeniedException   si el proyecto pertenece a otro usuario
     */
    @Transactional(readOnly = true)
    public Proyecto obtenerPorId(Long id, Long usuarioId) {

        /** Carga proyecto con usuario asociado para validar propiedad en la misma transacción. */
        Proyecto proyecto = proyectoRepository.findByIdWithUsuario(id)
                .orElseThrow(() -> new EntityNotFoundException("Proyecto no encontrado: " + id));

        /** Aplica control de acceso a nivel de servicio antes de devolver la entidad. */
        verificarPropiedad(proyecto, usuarioId);
        return proyecto;
    }

    /**
     * Crea un nuevo proyecto para el usuario autenticado.
     *
     * @param usuario usuario propietario del proyecto
     * @param dto     datos del formulario
     * @return proyecto creado y persistido
     */
    @Transactional
    public Proyecto crear(Usuario usuario, ProyectoDTO dto) {

        /** Construye la entidad desde el DTO recibido y el usuario autenticado. */
        Proyecto proyecto = Proyecto.builder()
                .usuario(usuario)
                .nombre(dto.getNombre())
                .sector(dto.getSector())
                .ubicacion(dto.getUbicacion())
                .descripcion(dto.getDescripcion())
                .build();

        /** Persiste y retorna la entidad creada con su ID generado. */
        return proyectoRepository.save(proyecto);
    }

    /**
     * Actualiza un proyecto existente verificando que pertenece al usuario.
     *
     * @param id        ID del proyecto a actualizar
     * @param usuarioId ID del usuario autenticado
     * @param dto       datos del formulario
     * @return proyecto actualizado
     */
    @Transactional
    public Proyecto actualizar(Long id, Long usuarioId, ProyectoDTO dto) {

        /** Reutiliza la validación de existencia y propiedad antes de modificar datos. */
        Proyecto proyecto = obtenerPorId(id, usuarioId);

        /** Actualización explícita campo a campo para control fino de cambios. */
        proyecto.setNombre(dto.getNombre());
        proyecto.setSector(dto.getSector());
        proyecto.setUbicacion(dto.getUbicacion());
        proyecto.setDescripcion(dto.getDescripcion());

        /** Guarda el estado actualizado de la entidad. */
        return proyectoRepository.save(proyecto);
    }

    /**
     * Elimina un proyecto verificando que pertenece al usuario.
     *
     * @param id        ID del proyecto a eliminar
     * @param usuarioId ID del usuario autenticado
     */
    @Transactional
    public void eliminar(Long id, Long usuarioId) {
        Proyecto proyecto = obtenerPorId(id, usuarioId);
        recomendacionRepository.deleteByProyectoId(id);
        proyectoRepository.delete(proyecto);
    }

    /**
     * Convierte un {@link Proyecto} en su {@link ProyectoDTO} equivalente.
     * Útil para precargar el formulario de edición.
     *
     * @param proyecto entidad
     * @return DTO con los datos del proyecto
     */
    public ProyectoDTO toDTO(Proyecto proyecto) {

        /** Mapeo manual para transferir solo datos necesarios hacia capa web. */
        ProyectoDTO dto = new ProyectoDTO();
        dto.setId(proyecto.getId());
        dto.setNombre(proyecto.getNombre());
        dto.setSector(proyecto.getSector());
        dto.setUbicacion(proyecto.getUbicacion());
        dto.setDescripcion(proyecto.getDescripcion());
        dto.setCreadoEn(proyecto.getCreadoEn());
        return dto;
    }

    /**
     * Verifica que el proyecto pertenece al usuario autenticado.
     * Lanza {@link AccessDeniedException} si no es así.
     */
    private void verificarPropiedad(Proyecto proyecto, Long usuarioId) {

        /** Deniega acceso si el propietario del proyecto no coincide con el usuario autenticado. */
        if (!proyecto.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("No tienes permiso para acceder a este proyecto.");
        }
    }
}