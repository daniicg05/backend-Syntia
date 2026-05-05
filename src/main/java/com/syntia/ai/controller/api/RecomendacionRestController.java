package com.syntia.ai.controller.api;

import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.RecomendacionDTO;
import com.syntia.ai.service.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Controlador REST unificado para recomendaciones de proyectos.
 * Rutas protegidas con JWT.
 */
@RestController
@RequestMapping("/api/usuario/proyectos/{proyectoId}/recomendaciones")
public class RecomendacionRestController {

    private final RecomendacionService recomendacionService;
    private final MotorMatchingService motorMatchingService;
    private final BusquedaRapidaService busquedaRapidaService;
    private final ProyectoService proyectoService;
    private final UsuarioService usuarioService;
    private final OpenAiGuiaService openAiGuiaService;
    private final PerfilService perfilService;
    private final BdnsClientService bdnsClientService;

    public RecomendacionRestController(RecomendacionService recomendacionService,
                                       MotorMatchingService motorMatchingService,
                                       BusquedaRapidaService busquedaRapidaService,
                                       ProyectoService proyectoService,
                                       UsuarioService usuarioService,
                                       OpenAiGuiaService openAiGuiaService,
                                       PerfilService perfilService,
                                       BdnsClientService bdnsClientService) {
        this.recomendacionService = recomendacionService;
        this.motorMatchingService = motorMatchingService;
        this.busquedaRapidaService = busquedaRapidaService;
        this.proyectoService = proyectoService;
        this.usuarioService = usuarioService;
        this.openAiGuiaService = openAiGuiaService;
        this.perfilService = perfilService;
        this.bdnsClientService = bdnsClientService;
    }

    // ─────────────────────────────────────────────
    // OBTENER RECOMENDACIONES CON FILTROS
    // ─────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<?> obtenerRecomendaciones(@PathVariable Long proyectoId,
                                                    @RequestParam(required = false) String tipo,
                                                    @RequestParam(required = false) String sector,
                                                    @RequestParam(required = false) String ubicacion,
                                                    Authentication authentication) {

        Usuario usuario = resolverUsuario(authentication);
        Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());

        List<RecomendacionDTO> todas = recomendacionService.filtrar(proyectoId, tipo, sector, ubicacion);

        List<RecomendacionDTO> vigentes = todas.stream()
                .filter(RecomendacionDTO::isVigente)
                .collect(Collectors.toList());
        List<RecomendacionDTO> noVigentes = todas.stream()
                .filter(r -> !r.isVigente())
                .collect(Collectors.toList());

        List<String> tipos    = recomendacionService.obtenerTiposDistintos(proyectoId);
        List<String> sectores = recomendacionService.obtenerSectoresDistintos(proyectoId);
        long total = recomendacionService.contarPorProyecto(proyectoId);

        return ResponseEntity.ok(Map.of(
                "recomendacionesVigentes", vigentes,
                "recomendacionesNoVigentes", noVigentes,
                "tipos", tipos,
                "sectores", sectores,
                "totalSinFiltro", total
        ));
    }

    // ─────────────────────────────────────────────
    // BUSCAR CANDIDATAS EN BDNS
    // ─────────────────────────────────────────────
    @PostMapping("/buscar-candidatas")
    public ResponseEntity<?> buscarCandidatas(@PathVariable Long proyectoId,
                                              Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());

        try {
            int encontradas = busquedaRapidaService.buscarYGuardarCandidatas(proyecto);
            String mensaje = (encontradas == 0)
                    ? "No se encontraron convocatorias vigentes para tu sector y ubicación."
                    : "Se han encontrado " + encontradas + " convocatorias para tu perfil.";
            return ResponseEntity.ok(Map.of("mensaje", mensaje, "total", encontradas));
        } catch (BdnsClientService.BdnsException e) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "No se pudo conectar con BDNS: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────
    // GENERAR RECOMENDACIONES (SYNC)
    // ─────────────────────────────────────────────
    @PostMapping("/generar")
    public ResponseEntity<?> generar(@PathVariable Long proyectoId,
                                     Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());

        List<?> generadas;
        try {
            generadas = motorMatchingService.generarRecomendaciones(proyecto);
        } catch (OpenAiClient.OpenAiUnavailableException e) {
            return ResponseEntity.status(503).body(Map.of("error", "IA no disponible: " + e.getMessage()));
        } catch (BdnsClientService.BdnsException e) {
            return ResponseEntity.status(503).body(Map.of("error", "BDNS no disponible: " + e.getMessage()));
        }

        long totalEnBd = recomendacionService.contarPorProyecto(proyectoId);
        String mensaje = (generadas.isEmpty() && totalEnBd == 0)
                ? "No hay candidatas para analizar. Usa primero 'buscar convocatorias'."
                : (generadas.isEmpty()
                ? "La IA no encontró convocatorias que superen el umbral de compatibilidad."
                : "Se han analizado " + generadas.size() + " de un total de " + totalEnBd + " convocatorias.");

        return ResponseEntity.ok(Map.of(
                "mensaje", mensaje,
                "totalGeneradas", generadas.size()
        ));
    }

    // ─────────────────────────────────────────────
    // GENERAR RECOMENDACIONES (SSE / STREAM)
    // ─────────────────────────────────────────────
    @GetMapping(value = "/generar-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generarStream(@PathVariable Long proyectoId,
                                    Authentication authentication) {

        SseEmitter emitter = new SseEmitter(180_000L);
        Usuario usuario = resolverUsuario(authentication);
        Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());

        CompletableFuture.runAsync(() -> {
            try {
                motorMatchingService.generarRecomendacionesStream(proyecto, emitter);
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("error", e.getMessage())));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> {});
        return emitter;
    }

    // ─────────────────────────────────────────────
    // GUIA ENRIQUECIDA
    // ─────────────────────────────────────────────
    @GetMapping("/{recomendacionId}/guia-enriquecida")
    public ResponseEntity<?> obtenerGuiaEnriquecida(@PathVariable Long proyectoId,
                                                    @PathVariable Long recomendacionId,
                                                    Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());

        Recomendacion rec = recomendacionService.obtenerEntidadPorId(recomendacionId, proyectoId);

        if (rec.getGuiaEnriquecida() != null && !rec.getGuiaEnriquecida().isBlank()) {
            GuiaSubvencionDTO guia = openAiGuiaService.deserializarGuia(rec.getGuiaEnriquecida());
            if (guia != null) return ResponseEntity.ok(guia);
        }

        try {
            var perfil = perfilService.obtenerPerfil(usuario.getId()).orElse(null);
            var convocatoria = rec.getConvocatoria();

            String detalleTexto = convocatoria.getIdBdns() != null
                    ? bdnsClientService.obtenerDetalleTexto(convocatoria.getIdBdns())
                    : null;

            String urlOficial = convocatoria.getNumeroConvocatoria() != null
                    ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + convocatoria.getNumeroConvocatoria()
                    : convocatoria.getUrlOficial();

            GuiaSubvencionDTO guia = openAiGuiaService.generarGuia(
                    proyecto, perfil, convocatoria, detalleTexto, urlOficial);

            String guiaJson = openAiGuiaService.serializarGuia(guia);
            recomendacionService.actualizarGuiaEnriquecida(recomendacionId, guiaJson);

            return ResponseEntity.ok(guia);

        } catch (OpenAiClient.OpenAiUnavailableException e) {
            return ResponseEntity.status(503).body(Map.of("error", "IA no disponible: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error generando la guía: " + e.getMessage()));
        }
    }

    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
    }
}
