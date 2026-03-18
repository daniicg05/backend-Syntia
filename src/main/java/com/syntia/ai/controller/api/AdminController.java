package com.syntia.ai.controller.api;

import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.ProyectoRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.service.ConvocatoriaService;
import com.syntia.ai.service.ProyectoService;
import com.syntia.ai.service.RecomendacionService;
import com.syntia.ai.service.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador REST unificado para administración.
 * 100% API REST: usuarios, convocatorias, métricas.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UsuarioService usuarioService;
    private final ConvocatoriaService convocatoriaService;
    private final ProyectoService proyectoService;
    private final RecomendacionService recomendacionService;
    private final ProyectoRepository proyectoRepository;
    private final RecomendacionRepository recomendacionRepository;

    public AdminController(UsuarioService usuarioService,
                           ConvocatoriaService convocatoriaService,
                           ProyectoService proyectoService,
                           RecomendacionService recomendacionService,
                           ProyectoRepository proyectoRepository,
                           RecomendacionRepository recomendacionRepository) {
        this.usuarioService = usuarioService;
        this.convocatoriaService = convocatoriaService;
        this.proyectoService = proyectoService;
        this.recomendacionService = recomendacionService;
        this.proyectoRepository = proyectoRepository;
        this.recomendacionRepository = recomendacionRepository;
    }

    // ─────────────────────────────────────────────
    // DASHBOARD ADMIN
    // ─────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication authentication) {
        Map<String, Object> data = Map.of(
                "adminEmail", authentication.getName(),
                "totalUsuarios", usuarioService.obtenerTodos().size(),
                "totalConvocatorias", convocatoriaService.obtenerTodas().size(),
                "totalProyectos", proyectoRepository.countAll(),
                "totalRecomendaciones", recomendacionRepository.countAll()
        );
        return ResponseEntity.ok(data);
    }

    // ─────────────────────────────────────────────
    // GESTIÓN DE USUARIOS
    // ─────────────────────────────────────────────

    @GetMapping("/usuarios")
    public ResponseEntity<?> listarUsuarios() {
        return ResponseEntity.ok(Map.of(
                "usuarios", usuarioService.obtenerTodos(),
                "roles", Rol.values()
        ));
    }

    @GetMapping("/usuarios/{id}")
    public ResponseEntity<?> detalleUsuario(@PathVariable Long id) {
        Usuario usuario = usuarioService.buscarPorId(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));
        List<Proyecto> proyectos = proyectoService.obtenerProyectos(id);
        Map<Long, Long> recsPerProyecto = proyectos.stream()
                .collect(Collectors.toMap(
                        Proyecto::getId,
                        p -> recomendacionService.contarPorProyecto(p.getId())
                ));
        return ResponseEntity.ok(Map.of(
                "usuarioDetalle", usuario,
                "proyectos", proyectos,
                "recsPerProyecto", recsPerProyecto
        ));
    }

    @PostMapping("/usuarios/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Long id,
                                        @RequestParam Rol rol,
                                        Authentication authentication) {
        Usuario admin = resolverUsuario(authentication);
        if (admin.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No puedes cambiar tu propio rol."));
        }
        usuarioService.cambiarRol(id, rol);
        return ResponseEntity.ok(Map.of("message", "Rol actualizado correctamente."));
    }

    @PostMapping("/usuarios/{id}/eliminar")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Long id,
                                             Authentication authentication) {
        Usuario admin = resolverUsuario(authentication);
        if (admin.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No puedes eliminarte a ti mismo."));
        }
        usuarioService.eliminar(id);
        return ResponseEntity.ok(Map.of("message", "Usuario eliminado correctamente."));
    }

    // ─────────────────────────────────────────────
    // GESTIÓN DE CONVOCATORIAS
    // ─────────────────────────────────────────────

    @GetMapping("/convocatorias")
    public ResponseEntity<?> listarConvocatorias() {
        return ResponseEntity.ok(convocatoriaService.obtenerTodas());
    }

    @PostMapping("/convocatorias")
    public ResponseEntity<?> crearConvocatoria(@Valid @RequestBody ConvocatoriaDTO dto) {
        convocatoriaService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Convocatoria creada correctamente."));
    }

    @GetMapping("/convocatorias/{id}")
    public ResponseEntity<?> detalleConvocatoria(@PathVariable Long id) {
        ConvocatoriaDTO dto = convocatoriaService.toDTO(convocatoriaService.obtenerPorId(id));
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/convocatorias/{id}")
    public ResponseEntity<?> editarConvocatoria(@PathVariable Long id,
                                                @Valid @RequestBody ConvocatoriaDTO dto) {
        convocatoriaService.actualizar(id, dto);
        return ResponseEntity.ok(Map.of("message", "Convocatoria actualizada correctamente."));
    }

    @DeleteMapping("/convocatorias/{id}")
    public ResponseEntity<?> eliminarConvocatoria(@PathVariable Long id) {
        convocatoriaService.eliminar(id);
        return ResponseEntity.ok(Map.of("message", "Convocatoria eliminada correctamente."));
    }

    @PostMapping("/convocatorias/importar-bdns")
    public ResponseEntity<?> importarDesdeBdns(@RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamano) {
        try {
            int nuevas = convocatoriaService.importarDesdeBdns(pagina, tamano);
            if (nuevas == 0) {
                return ResponseEntity.ok(Map.of(
                        "message", "Se consultó BDNS pero no se encontraron convocatorias nuevas."
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "message", "Se importaron " + nuevas + " convocatorias nuevas desde BDNS."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "No se pudo conectar con la API de BDNS: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────
    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
    }
}