package com.syntia.ai.controller;

import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.ProyectoDTO;
import com.syntia.ai.service.ProyectoService;
import com.syntia.ai.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de proyectos del usuario autenticado.
 *
 * <p>Este controlador expone operaciones CRUD sobre proyectos en el contexto
 * del usuario actual (obtenido desde Spring Security). Todas las operaciones
 * se ejecutan bajo la ruta base:
 * <code>/api/usuario/proyectos</code>.</p>
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 * <li>Resolver al usuario autenticado a partir del email en el token/sesión.</li>
 * <li>Delegar la lógica de negocio al servicio {@link ProyectoService}.</li>
 * <li>Transformar entidades a DTO cuando corresponde.</li>
 * <li>Retornar respuestas HTTP con códigos adecuados (200,201,204).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/usuario/proyectos")
public class ProyectoController {

    /**
     * Servicio de negocio para operaciones de proyectos.
     * Se inyecta por constructor (inmutabilidad y facilidad de testeo).
     */
    private final ProyectoService proyectoService;

    /** Servicio para obtener información del usuario autenticado. */
    private final UsuarioService usuarioService;

    /**
     * Constructor principal del controlador.
     *
     * <p>\*Nota importante\*: el nombre del constructor debe coincidir exactamente
     * con el de la clase (<code>ProyectoController</code>), de lo contrario
     * Java lo interpreta como un método y produce error de compilación.</p>
     *
     * @param proyectoService servicio de proyectos
     * @param usuarioService servicio de usuarios
     */
    public ProyectoController(ProyectoService proyectoService, UsuarioService usuarioService) {
        this.proyectoService = proyectoService;
        this.usuarioService = usuarioService;
    }

    /** LISTAR:
     *
     * Lista todos los proyectos del usuario autenticado.
     *
     * <p>Flujo:</p>
     * <ol>
     * <li>Se resuelve el usuario actual con {@link #resolverUsuario(Authentication)}.</li>
     * <li>Se consultan proyectos por ID de usuario en el servicio.</li>
     * <li>Se transforman entidades a {@link ProyectoDTO}.</li>
     * <li>Se retorna HTTP200 con la lista.</li>
     * </ol>
     *
     * @param authentication contexto de autenticación de Spring Security
     * @return lista de proyectos del usuario en formato DTO
     */
    @GetMapping
    public ResponseEntity<List<ProyectoDTO>> listar(Authentication authentication) {

        /** Obtiene el usuario de dominio asociado al principal autenticado. */
        Usuario usuario = resolverUsuario(authentication);

        /** Consulta proyectos del usuario y mapea cada entidad a DTO. */
        List<ProyectoDTO> dtos = proyectoService.obtenerProyectos(usuario.getId())
                .stream()
                .map(proyectoService::toDTO)
                .toList();

        /** Retorna HTTP200 OK con el arreglo de proyectos. */
        return ResponseEntity.ok(dtos);
    }

    /** OBTENER POR ID:
     *
     * Obtiene un proyecto específico por su ID, validando pertenencia al usuario autenticado.
     *
     * @param id identificador del proyecto
     * @param authentication contexto de autenticación * @return proyecto encontrado en formato DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProyectoDTO> obtener(@PathVariable Long id, Authentication authentication) {

        /** Resuelve usuario actual. */
        Usuario usuario = resolverUsuario(authentication);

        /** Busca proyecto por ID en contexto de usuario y convierte a DTO. */
        ProyectoDTO dto = proyectoService.toDTO(proyectoService.obtenerPorId(id, usuario.getId()));

        /** Retorna HTTP200 OK con el proyecto solicitado. */
        return ResponseEntity.ok(dto);
    }

    /** CREAR:
     *
     * Crea un nuevo proyecto para el usuario autenticado.
     *
     * <p>El cuerpo de la petición se valida con Bean Validation gracias a {@link Valid}.</p>
     *
     * @param dto datos del proyecto a crear
     * @param authentication contexto de autenticación
     * @return proyecto creado en formato DTO
     */
    @PostMapping
    public ResponseEntity<ProyectoDTO> crear(@Valid @RequestBody ProyectoDTO dto, Authentication authentication) {

        /** Obtiene usuario autenticado. */
        Usuario usuario = resolverUsuario(authentication);

        /** Crea proyecto y convierte resultado a DTO para respuesta. */
        ProyectoDTO creado = proyectoService.toDTO(proyectoService.crear(usuario, dto));

        /** Retorna HTTP201 Created con el recurso creado. */
        return ResponseEntity.status(201).body(creado);
    }

    /** ACTUALIZAR:
     *
     * Actualiza un proyecto existente del usuario autenticado.
     *
     * @param id identificador del proyecto a actualizar
     * @param dto datos nuevos del proyecto
     * @param authentication contexto de autenticación
     * @return proyecto actualizado en formato DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProyectoDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProyectoDTO dto,
            Authentication authentication
    ) {

        /** Resuelve el usuario propietario de la operación. */
        Usuario usuario = resolverUsuario(authentication);

        /** Actualiza en capa de servicio controlando el ámbito del usuario. */
        ProyectoDTO actualizado = proyectoService.toDTO(proyectoService.actualizar(id, usuario.getId(), dto));

        /** Retorna HTTP200 OK con el recurso actualizado. */
        return ResponseEntity.ok(actualizado);
    }

    /** ELIMINAR:
     *
     * Elimina un proyecto del usuario autenticado.
     *
     * @param id identificador del proyecto a eliminar
     * @param authentication contexto de autenticación
     * @return respuesta vacía con HTTP204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {

        /** Obtiene usuario actual para restringir eliminación a sus propios proyectos. */
        Usuario usuario = resolverUsuario(authentication);

        /** Ejecuta eliminación en servicio. */
        proyectoService.eliminar(id, usuario.getId());

        /** Retorna HTTP204 No Content: operación exitosa sin cuerpo de respuesta. */
        return ResponseEntity.noContent().build();
    }

    /** MÉTODO AUXILIAR:
     *
     * Método auxiliar para resolver el usuario de dominio a partir del principal autenticado.
     *
     * <p>Se usa el nombre de autenticación (normalmente email) para buscar el usuario.</p>
     *
     * @param authentication objeto de seguridad con el principal actual
     * @return usuario de dominio existente en base de datos
     * @throws UsernameNotFoundException si no existe usuario para el email autenticado
     */
    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
    }
}