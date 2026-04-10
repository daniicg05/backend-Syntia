package com.syntia.ai.controller.api;

import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.SyncState;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.repository.SyncStateRepository;
import com.syntia.ai.model.dto.AdminDetalleUsuarioResponseDTO;
import com.syntia.ai.model.dto.AdminUsuarioDetalleDTO;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.model.dto.HistorialCorreoDTO;
import com.syntia.ai.model.dto.ImportacionBdnsEstadoDTO;
import com.syntia.ai.repository.ProyectoRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.service.BdnsEtlPanelService;
import com.syntia.ai.service.BdnsImportJobService;
import com.syntia.ai.service.ModoImportacion;
import com.syntia.ai.service.ConvocatoriaService;
import com.syntia.ai.service.PerfilService;
import com.syntia.ai.service.ProyectoService;
import com.syntia.ai.service.RecomendacionService;
import com.syntia.ai.service.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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

    private static final int CONVOCATORIAS_POR_PAGINA = 50;

    private final UsuarioService usuarioService;
    private final PerfilService perfilService;
    private final ConvocatoriaService convocatoriaService;
    private final ProyectoService proyectoService;
    private final RecomendacionService recomendacionService;
    private final ProyectoRepository proyectoRepository;
    private final RecomendacionRepository recomendacionRepository;
    private final BdnsImportJobService bdnsImportJobService;
    private final BdnsEtlPanelService bdnsEtlPanelService;
    private final SyncStateRepository syncStateRepository;

    public AdminController(UsuarioService usuarioService,
                           PerfilService perfilService,
                           ConvocatoriaService convocatoriaService,
                           ProyectoService proyectoService,
                           RecomendacionService recomendacionService,
                           ProyectoRepository proyectoRepository,
                           RecomendacionRepository recomendacionRepository,
                           BdnsImportJobService bdnsImportJobService,
                           BdnsEtlPanelService bdnsEtlPanelService,
                           SyncStateRepository syncStateRepository) {
        this.usuarioService = usuarioService;
        this.perfilService = perfilService;
        this.convocatoriaService = convocatoriaService;
        this.proyectoService = proyectoService;
        this.recomendacionService = recomendacionService;
        this.proyectoRepository = proyectoRepository;
        this.recomendacionRepository = recomendacionRepository;
        this.bdnsImportJobService = bdnsImportJobService;
        this.bdnsEtlPanelService = bdnsEtlPanelService;
        this.syncStateRepository = syncStateRepository;
    }

    // ─────────────────────────────────────────────
    // DASHBOARD ADMIN
    // ─────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication authentication) {
        Map<String, Object> data = Map.of(
                "adminEmail", authentication.getName(),
                "totalUsuarios", usuarioService.obtenerTodos().size(),
                "totalConvocatorias", convocatoriaService.contarTodas(),
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

        Perfil perfil = perfilService.obtenerPerfil(id).orElse(null);
        List<Proyecto> proyectos = proyectoService.obtenerProyectos(id);
        Map<Long, Long> recsPerProyecto = proyectos.stream()
                .collect(Collectors.toMap(
                        Proyecto::getId,
                        p -> recomendacionService.contarPorProyecto(p.getId())
                ));

        AdminUsuarioDetalleDTO usuarioDto = AdminUsuarioDetalleDTO.builder()
                .id(usuario.getId())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .creadoEn(usuario.getCreadoEn())
                .empresa(perfil != null ? perfil.getEmpresa() : null)
                .provincia(perfil != null ? perfil.getProvincia() : null)
                .telefono(perfil != null ? perfil.getTelefono() : null)
                .build();

        List<Map<String, Object>> proyectosDto = proyectos.stream()
                .map(p -> {
                    Map<String, Object> proyectoMap = new java.util.HashMap<>();
                    proyectoMap.put("id", p.getId());
                    proyectoMap.put("nombre", p.getNombre() != null ? p.getNombre() : "");
                    proyectoMap.put("sector", p.getSector() != null ? p.getSector() : "");
                    return proyectoMap;
                })
                .toList();

        List<HistorialCorreoDTO> historialDto = usuarioService.obtenerHistorialCorreo(id).stream()
                .map(h -> HistorialCorreoDTO.builder()
                        .anterior(h.getAnterior())
                        .nuevo(h.getNuevo())
                        .fecha(h.getFecha())
                        .actor(h.getActor())
                        .build())
                .toList();

        AdminDetalleUsuarioResponseDTO response = AdminDetalleUsuarioResponseDTO.builder()
                .usuario(usuarioDto)
                .proyectos(proyectosDto)
                .recsPerProyecto(recsPerProyecto)
                .emailCambiado(usuarioService.emailCambiado(id))
                .historialCorreo(historialDto)
                .build();

        return ResponseEntity.ok(response);
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
    public ResponseEntity<?> listarConvocatorias(@RequestParam(defaultValue = "0") int page) {
        Page<?> convocatoriasPage = convocatoriaService.obtenerPagina(page, CONVOCATORIAS_POR_PAGINA);
        return ResponseEntity.ok(Map.of(
                "convocatorias", convocatoriasPage.getContent(),
                "page", convocatoriasPage.getNumber(),
                "size", convocatoriasPage.getSize(),
                "totalElements", convocatoriasPage.getTotalElements(),
                "totalPages", convocatoriasPage.getTotalPages(),
                "hasNext", convocatoriasPage.hasNext()
        ));
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
            @RequestParam(defaultValue = "FULL") String modo,
            @RequestParam(defaultValue = "-1") long delayMs) {
        ModoImportacion modoImportacion;
        try {
            modoImportacion = ModoImportacion.valueOf(modo.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Modo inválido. Valores permitidos: FULL, INCREMENTAL"));
        }
        boolean iniciado = bdnsImportJobService.iniciar(modoImportacion, delayMs);
        if (!iniciado) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya hay una importación BDNS en curso."));
        }
        return ResponseEntity.accepted()
                .body(Map.of("message", "Importación masiva BDNS iniciada en segundo plano.",
                             "modo", modoImportacion.name(),
                             "delayMs", delayMs < 0 ? "configuración por defecto" : delayMs));
    }

    /**
     * Permite saltar directamente a una página concreta sin esperar a procesarlas todas.
     * Útil para recuperarse de reinicios inesperados cuando ya se sabe hasta qué página
     * se llegó. Fuerza el estado a ERROR para que el modo INCREMENTAL retome desde ahí.
     */
    @PutMapping("/bdns/sync-state/pagina")
    public ResponseEntity<?> establecerPaginaInicio(@RequestParam int pagina) {
        if (pagina < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "La página debe ser >= 0"));
        }
        SyncState state = syncStateRepository.findByEje("GLOBAL")
                .orElse(SyncState.builder().eje("GLOBAL").build());
        state.setUltimaPaginaOk(pagina);
        state.setEstado(SyncState.Estado.ERROR);
        syncStateRepository.save(state);
        return ResponseEntity.ok(Map.of(
                "message", "Punto de reanudación establecido. La próxima importación INCREMENTAL empezará desde la página " + (pagina + 1),
                "ultimaPaginaOk", pagina,
                "siguientePagina", pagina + 1
        ));
    }

    @DeleteMapping("/bdns/importar")
    public ResponseEntity<?> cancelarImportacionBdns() {
        boolean cancelado = bdnsImportJobService.cancelar();
        if (!cancelado) return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "No hay importación en curso"));
        return ResponseEntity.ok(Map.of("mensaje", "Cancelación solicitada"));
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

    /**
     * Métricas de cobertura: qué % de convocatorias en BD tiene cada campo relleno.
     */
    @GetMapping("/bdns/cobertura")
    public ResponseEntity<?> coberturaDatos() {
        return ResponseEntity.ok(bdnsEtlPanelService.obtenerCobertura());
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