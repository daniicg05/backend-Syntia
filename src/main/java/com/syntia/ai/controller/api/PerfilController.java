package com.syntia.ai.controller.api;

import com.syntia.ai.model.ErrorResponse;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.CambiarEmailDTO;
import com.syntia.ai.model.dto.CambiarPasswordDTO;
import com.syntia.ai.model.dto.LoginResponseDTO;
import com.syntia.ai.model.dto.PerfilDTO;
import com.syntia.ai.security.JwtService;
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

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/usuario/perfil")
public class PerfilController {

    private final PerfilService perfilService;
    private final UsuarioService usuarioService;
    private final JwtService jwtService;

    public PerfilController(PerfilService perfilService, UsuarioService usuarioService, JwtService jwtService) {
        this.perfilService = perfilService;
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
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

    @GetMapping("/completo")
    public ResponseEntity<?> obtenerPerfilCompleto(Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("email", usuario.getEmail());
        resultado.put("rol", usuario.getRol().name());
        resultado.put("plan", usuario.getPlan().name());
        resultado.put("creadoEn", usuario.getCreadoEn());

        perfilService.obtenerPerfil(usuario.getId()).ifPresent(perfil -> {
            resultado.put("nombre", perfil.getNombre());
            resultado.put("sector", perfil.getSector());
            resultado.put("ubicacion", perfil.getUbicacion());
            resultado.put("empresa", perfil.getEmpresa());
            resultado.put("provincia", perfil.getProvincia());
            resultado.put("telefono", perfil.getTelefono());
            resultado.put("tipoEntidad", perfil.getTipoEntidad());
            resultado.put("objetivos", perfil.getObjetivos());
            resultado.put("necesidadesFinanciacion", perfil.getNecesidadesFinanciacion());
            resultado.put("descripcionLibre", perfil.getDescripcionLibre());
        });

        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/estado")
    public ResponseEntity<?> estadoPerfil(Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        boolean perfilCompleto = perfilService.tienePerfil(usuario.getId());
        return ResponseEntity.ok(Map.of("perfilCompleto", perfilCompleto));
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

    @PutMapping("/email")
    public ResponseEntity<?> cambiarEmail(@Valid @RequestBody CambiarEmailDTO dto,
                                          Authentication authentication,
                                          HttpServletRequest request) {
        Usuario usuario = resolverUsuario(authentication);

        try {
            Usuario actualizado = usuarioService.cambiarEmail(
                    usuario.getId(),
                    dto.getPasswordActual(),
                    dto.getNuevoEmail(),
                    authentication.getName()
            );
            String nuevoToken = jwtService.generarToken(actualizado.getEmail(), actualizado.getRol().name());
            return ResponseEntity.ok(new LoginResponseDTO(nuevoToken, actualizado.getEmail(), actualizado.getRol().name(), 86400000L));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage(),
                            java.time.LocalDateTime.now(), request.getRequestURI()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage(),
                            java.time.LocalDateTime.now(), request.getRequestURI()));
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> cambiarPassword(@Valid @RequestBody CambiarPasswordDTO dto,
                                             Authentication authentication,
                                             HttpServletRequest request) {

        Usuario usuario = resolverUsuario(authentication);

        try {
            usuarioService.cambiarPasswordAutenticado(
                    usuario.getId(),
                    dto.getPasswordActual(),
                    dto.getNuevaPassword(),
                    dto.getConfirmarPassword()
            );

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            HttpStatus.BAD_REQUEST.value(),
                            e.getMessage(),
                            java.time.LocalDateTime.now(),
                            request.getRequestURI()
                    ));
        }
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
        dto.setNombre(perfil.getNombre());
        return dto;
    }
}