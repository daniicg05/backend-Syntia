package com.syntia.ai.controller;

import com.syntia.ai.model.ErrorResponse;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.PerfilDTO;
import com.syntia.ai.service.PerfilService;
import com.syntia.ai.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuario/perfil")
public class PerfilController {

    private final PerfilService perfilService;
    private final UsuarioService usuarioService;

    public PerfilController(PerfilService perfilService, UsuarioService usuarioService) {
        this.perfilService = perfilService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<?> obtenerPerfil(Authentication authentication, HttpServletRequest request) {
        Usuario usuario = resolverUsuario(authentication);

        return perfilService.obtenerPerfil(usuario.getId())
                .<ResponseEntity<?>>map(perfil -> ResponseEntity.ok(convertirADTO(perfil)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(
                                HttpStatus.NOT_FOUND.value(),
                                "El usuario aún no ha completado su perfil",
                                java.time.LocalDateTime.now(),
                                request.getRequestURI()
                        )));
    }

    @GetMapping("/ver")
    public ResponseEntity<?> verPerfil(Authentication authentication, HttpServletRequest request) {
        return obtenerPerfil(authentication, request);
    }

    @PutMapping
    public ResponseEntity<?> guardarPerfil(@Valid @RequestBody PerfilDTO dto,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        Usuario usuario = resolverUsuario(authentication);

        if (perfilService.tienePerfil(usuario.getId())) {
            perfilService.actualizarPerfil(usuario.getId(), dto);
        } else {
            perfilService.crearPerfil(usuario, dto);
        }

        return perfilService.obtenerPerfil(usuario.getId())
                .<ResponseEntity<?>>map(perfil -> ResponseEntity.ok(convertirADTO(perfil)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "No se pudo guardar el perfil correctamente",
                                java.time.LocalDateTime.now(),
                                request.getRequestURI()
                        )));
    }

    @PostMapping
    public ResponseEntity<?> guardarPerfilPost(@Valid @RequestBody PerfilDTO dto,
                                               Authentication authentication,
                                               HttpServletRequest request) {
        return guardarPerfil(dto, authentication, request);
    }

    private Usuario resolverUsuario(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UsernameNotFoundException("Usuario no autenticado");
        }

        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuario no encontrado: " + authentication.getName()));
    }

    private PerfilDTO convertirADTO(Perfil perfil) {
        PerfilDTO dto = new PerfilDTO();
        BeanUtils.copyProperties(perfil, dto, "usuario");
        return dto;
    }
}