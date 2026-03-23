package com.syntia.ai.controller.api;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.RecomendacionDTO;
import com.syntia.ai.service.BdnsClientService;
import com.syntia.ai.service.MotorMatchingService;
import com.syntia.ai.service.OpenAiGuiaService;
import com.syntia.ai.service.PerfilService;
import com.syntia.ai.service.ProyectoService;
import com.syntia.ai.service.RecomendacionService;
import com.syntia.ai.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuario/proyectos/{proyectoId}/recomendaciones")
@PreAuthorize("hasRole('USUARIO')")
public class RecomendacionController {

    private final RecomendacionService recomendacionService;
    private final MotorMatchingService motorMatchingService;
    private final OpenAiGuiaService openAiGuiaService;
    private final BdnsClientService bdnsClientService;
    private final ProyectoService proyectoService;
    private final PerfilService perfilService;
    private final UsuarioService usuarioService;

    public RecomendacionController(RecomendacionService recomendacionService,
                                   MotorMatchingService motorMatchingService,
                                   OpenAiGuiaService openAiGuiaService,
                                   BdnsClientService bdnsClientService,
                                   ProyectoService proyectoService,
                                   PerfilService perfilService,
                                   UsuarioService usuarioService) {
        this.recomendacionService = recomendacionService;
        this.motorMatchingService = motorMatchingService;
        this.openAiGuiaService = openAiGuiaService;
        this.bdnsClientService = bdnsClientService;
        this.proyectoService = proyectoService;
        this.perfilService = perfilService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<RecomendacionDTO>> listar(@PathVariable Long proyectoId,
                                                         Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        proyectoService.obtenerPorId(proyectoId, usuario.getId());
        return ResponseEntity.ok(recomendacionService.obtenerPorProyecto(proyectoId));
    }

    @PostMapping("/generar")
    public ResponseEntity<?> generar(@PathVariable Long proyectoId,
                                     Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());

        List<Recomendacion> recomendaciones = motorMatchingService.generarRecomendaciones(proyecto);

        if (recomendaciones.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No se encontraron recomendaciones. Asegúrate de haber buscado convocatorias primero.",
                    "recomendaciones", List.of()
            ));
        }

        List<RecomendacionDTO> dtos = recomendaciones.stream()
                .map(recomendacionService::toDTO)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{recId}/guia-enriquecida")
    public ResponseEntity<?> guiaEnriquecida(@PathVariable Long proyectoId,
                                             @PathVariable Long recId,
                                             Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        Proyecto proyecto = proyectoService.obtenerPorId(proyectoId, usuario.getId());
        Recomendacion rec = recomendacionService.obtenerEntidadPorId(recId, proyectoId);

        if (rec.getGuiaEnriquecida() != null && !rec.getGuiaEnriquecida().isBlank()) {
            GuiaSubvencionDTO cached = openAiGuiaService.deserializarGuia(rec.getGuiaEnriquecida());
            if (cached != null) {
                return ResponseEntity.ok(cached);
            }
        }

        Perfil perfil = perfilService.obtenerPerfil(usuario.getId()).orElse(null);
        Convocatoria convocatoria = rec.getConvocatoria();

        String detalleTexto = convocatoria.getIdBdns() != null
                ? bdnsClientService.obtenerDetalleTexto(convocatoria.getIdBdns())
                : null;

        String numConv = convocatoria.getNumeroConvocatoria();
        String urlOficial = (numConv != null && !numConv.isBlank())
                ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                : convocatoria.getUrlOficial();

        GuiaSubvencionDTO guia = openAiGuiaService.generarGuia(proyecto, perfil, convocatoria, detalleTexto, urlOficial);
        recomendacionService.actualizarGuiaEnriquecida(recId, openAiGuiaService.serializarGuia(guia));

        return ResponseEntity.ok(guia);
    }

    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
    }
}