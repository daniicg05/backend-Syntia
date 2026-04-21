package com.syntia.ai.controller.api;

import com.syntia.ai.model.Usuario;
import com.syntia.ai.service.ConvocatoriaAnalisisIAService;
import com.syntia.ai.service.UsuarioService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Endpoint SSE para el análisis enriquecido IA de una convocatoria BDNS.
 *
 * GET /api/usuario/proyectos/{proyectoId}/convocatorias/{idBdns}/analisis-ia
 *
 * Emite eventos SSE:
 *   - "estado"     → mensajes de progreso (String)
 *   - "detalle"    → campos BDNS mapeados (ConvocatoriaAnalisisIADTO parcial)
 *   - "analisis"   → campos IA generados (Map)
 *   - "completado" → DTO completo enriquecido (ConvocatoriaAnalisisIADTO)
 *   - "error"      → mensaje de error (String)
 */
@RestController
@RequestMapping("/api/usuario/proyectos/{proyectoId}/convocatorias")
@PreAuthorize("hasRole('USUARIO')")
public class ConvocatoriaAnalisisIAController {

    private final ConvocatoriaAnalisisIAService analisisIAService;
    private final UsuarioService usuarioService;

    public ConvocatoriaAnalisisIAController(ConvocatoriaAnalisisIAService analisisIAService,
                                            UsuarioService usuarioService) {
        this.analisisIAService = analisisIAService;
        this.usuarioService = usuarioService;
    }

    @GetMapping(value = "/{idBdns}/analisis-ia", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analisisIA(@PathVariable Long proyectoId,
                                 @PathVariable Long idBdns,
                                 Authentication authentication) {

        // Verificar usuario autenticado (mismo patrón que RecomendacionController)
        resolverUsuario(authentication);

        SseEmitter emitter = new SseEmitter(300_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> {
            try {
                analisisIAService.generarAnalisisStream(idBdns, emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });

        return emitter;
    }

    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
    }
}