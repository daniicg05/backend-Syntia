
package com.syntia.ai.controller.api;

import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.ConvocatoriaFavoritaDTO;
import com.syntia.ai.service.ConvocatoriaFavoritaService;
import com.syntia.ai.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/usuario/favoritas")
@PreAuthorize("hasRole('USUARIO')")
public class FavoritasController {

    private final ConvocatoriaFavoritaService favoritaService;
    private final UsuarioService usuarioService;

    public FavoritasController(ConvocatoriaFavoritaService favoritaService,
                               UsuarioService usuarioService) {
        this.favoritaService = favoritaService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<ConvocatoriaFavoritaDTO>> listar(Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        return ResponseEntity.ok(favoritaService.listarFavoritas(usuario.getId()));
    }

    @GetMapping("/ids")
    public ResponseEntity<Set<Long>> listarIds(Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        return ResponseEntity.ok(favoritaService.listarIdsFavoritas(usuario.getId()));
    }

    @GetMapping("/{convocatoriaId}/estado")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable Long convocatoriaId,
                                                      Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        boolean favorita = favoritaService.esFavorita(usuario.getId(), convocatoriaId);
        return ResponseEntity.ok(Map.of(
                "convocatoriaId", convocatoriaId,
                "favorita", favorita
        ));
    }

    @PostMapping("/{convocatoriaId}")
    public ResponseEntity<Map<String, Object>> marcar(@PathVariable Long convocatoriaId,
                                                       Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        favoritaService.marcarFavorita(usuario.getId(), convocatoriaId);
        return ResponseEntity.ok(Map.of(
                "convocatoriaId", convocatoriaId,
                "favorita", true,
                "message", "Convocatoria marcada como favorita"
        ));
    }

    @DeleteMapping("/{convocatoriaId}")
    public ResponseEntity<Map<String, Object>> desmarcar(@PathVariable Long convocatoriaId,
                                                          Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        favoritaService.desmarcarFavorita(usuario.getId(), convocatoriaId);
        return ResponseEntity.ok(Map.of(
                "convocatoriaId", convocatoriaId,
                "favorita", false,
                "message", "Convocatoria eliminada de favoritas"
        ));
    }

    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + authentication.getName()));
    }
}

