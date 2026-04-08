package com.syntia.ai.controller.api;

import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.model.dto.ImportacionBdnsEstadoDTO;
import com.syntia.ai.repository.ProyectoRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.service.BdnsEtlPanelService;
import com.syntia.ai.service.BdnsImportJobService;
import com.syntia.ai.service.ModoImportacion;
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
    private final BdnsImportJobService bdnsImportJobService;
    private final BdnsEtlPanelService bdnsEtlPanelService;

    public AdminController(UsuarioService usuarioService,
                           ConvocatoriaService convocatoriaService,
                           ProyectoService proyectoService,
                           RecomendacionService recomendacionService,
                           ProyectoRepository proyectoRepository,
                           RecomendacionRepository recomendacionRepository,
                           BdnsImportJobService bdnsImportJobService,
                           BdnsEtlPanelService bdnsEtlPanelService) {
        this.usuarioService = usuarioService;
        this.convocatoriaService = convocatoriaService;
        this.proyectoService = proyectoService;
        this.recomendacionService = recomendacionService;
        this.proyectoRepository = proyectoRepository;
        this.recomendacionRepository = recomendacionRepository;
        this.bdnsImportJobService = bdnsImportJobService;
        this.bdnsEtlPanelService = bdnsEtlPanelService;
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
                "proyectos", proyectos.stream().map(proyectoService::toDTO).toList(),
                "recsPerProyecto", recsPerProyecto
        ));
    }

    @PutMapping("/usuarios/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Long id,
                                        @RequestBody Map<String, String> body,
                                        Authentication authentication) {
        Rol rol = Rol.valueOf(body.get("rol"));
        Usuario admin = resolverUsuario(authentication);
        if (admin.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No puedes cambiar tu propio rol."));
        }
        usuarioService.cambiarRol(id, rol);
        return ResponseEntity.ok(Map.of("message", "Rol actualizado correctamente."));
    }

    @DeleteMapping("/usuarios/{id}")
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
    // IMPORTACIÓN MASIVA BDNS
    // ─────────────────────────────────────────────

    @PostMapping("/bdns/importar")
    public ResponseEntity<?> lanzarImportacionBdns(
            @RequestParam(defaultValue = "FULL") String modo) {
        ModoImportacion modoImportacion;
        try {
            modoImportacion = ModoImportacion.valueOf(modo.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Modo inválido. Valores permitidos: FULL, INCREMENTAL"));
        }
        boolean iniciado = bdnsImportJobService.iniciar(modoImportacion);
        if (!iniciado) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya hay una importación BDNS en curso."));
        }
        return ResponseEntity.accepted()
                .body(Map.of("message", "Importación masiva BDNS iniciada en segundo plano.",
                             "modo", modoImportacion.name()));
    }

    @GetMapping("/bdns/estado")
    public ResponseEntity<ImportacionBdnsEstadoDTO> estadoImportacionBdns() {
        BdnsImportJobService.EstadoJob job = bdnsImportJobService.obtenerEstado();
        return ResponseEntity.ok(new ImportacionBdnsEstadoDTO(
                job.estado().name(),
                job.registrosImportados(),
                job.ejeActual(),
                job.iniciadoEn(),
                job.finalizadoEn(),
                job.error(),
                job.modo() != null ? job.modo().name() : null
        ));
    }

    /**
     * Lista el estado actual de los 23 ejes territoriales desde la tabla sync_state.
     * Devuelve solo los ejes que han sido procesados al menos una vez.
     */
    @GetMapping("/bdns/ejes")
    public ResponseEntity<?> estadoEjesBdns() {
        return ResponseEntity.ok(bdnsEtlPanelService.obtenerEstadoEjes());
    }

    /**
     * Historial de ejecuciones resumido: una entrada por ejecucionId con stats agregadas.
     * Ordenado de más reciente a más antiguo.
     */
    @GetMapping("/bdns/historial")
    public ResponseEntity<?> historialImportaciones() {
        return ResponseEntity.ok(bdnsEtlPanelService.obtenerHistorial());
    }

    /**
     * Detalle página a página de una ejecución concreta.
     */
    @GetMapping("/bdns/historial/{ejecucionId}")
    public ResponseEntity<?> detalleEjecucion(@PathVariable String ejecucionId) {
        return ResponseEntity.ok(bdnsEtlPanelService.obtenerLogsEjecucion(ejecucionId));
    }

    @GetMapping("/bdns/ultima-importacion")
    public ResponseEntity<?> ultimaImportacionBdns() {
        BdnsImportJobService.EstadoJob job = bdnsImportJobService.obtenerEstado();
        if (job.estado() == BdnsImportJobService.EstadoImportacion.INACTIVO) {
            return ResponseEntity.ok(Map.of("message", "Nunca se ha realizado una importación masiva."));
        }
        return ResponseEntity.ok(Map.of(
                "estado", job.estado().name(),
                "registrosImportados", job.registrosImportados(),
                "finalizadoEn", job.finalizadoEn() != null ? job.finalizadoEn().toString() : "en curso"
        ));
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