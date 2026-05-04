package com.syntia.ai.controller.api;

import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.LoginRequestDTO;
import com.syntia.ai.model.dto.LoginResponseDTO;
import com.syntia.ai.model.dto.RegistroDTO;
import com.syntia.ai.security.JwtService;
import com.syntia.ai.service.DashboardService;
import com.syntia.ai.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST unificado para autenticación y dashboards.
 * Arquitectura 100% API REST.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioService usuarioService;
    private final DashboardService dashboardService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UsuarioService usuarioService,
                          DashboardService dashboardService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
        this.dashboardService = dashboardService;
    }

    // ==========================
    // LANDING PAGE PÚBLICA
    // ==========================
    @GetMapping("/")
    public ResponseEntity<?> home() {
        return ResponseEntity.ok(Map.of(
                "message", "Bienvenido a la API de Syntia MVP"
        ));
    }

    // ==========================
    // AVISO LEGAL
    // ==========================
    @GetMapping("/aviso-legal")
    public ResponseEntity<?> avisoLegal() {
        return ResponseEntity.ok(Map.of(
                "empresa", "Syntia",
                "legal", "Texto legal de ejemplo",
                "version", "MVP"
        ));
    }

    // ==========================
    // REGISTRO
    // ==========================
    @PostMapping("/auth/registro")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroDTO dto) {

        if (!dto.getPassword().equals(dto.getConfirmarPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Las contraseñas no coinciden"));
        }

        String emailNormalizado = dto.getEmail().toLowerCase().strip();
        try {
            usuarioService.registrar(emailNormalizado, dto.getPassword(), Rol.USUARIO);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

        Usuario usuarioCreado = usuarioService.buscarPorEmail(emailNormalizado)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado tras registro"));

        String token = jwtService.generarToken(usuarioCreado.getEmail(), usuarioCreado.getRol().name());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LoginResponseDTO(token, usuarioCreado.getEmail(), usuarioCreado.getRol().name(), jwtExpiration));
    }

    // ==========================
    // LOGIN FORM INFO (solo para referencia)
    // ==========================
    @GetMapping("/auth/login")
    public ResponseEntity<?> loginInfo() {
        return ResponseEntity.ok(Map.of(
                "endpoint", "/api/auth/login",
                "method", "POST",
                "fields", "email, password"
        ));
    }

    // ==========================
    // LOGIN JWT
    // ==========================
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO request) {

        String emailLogin = request.getEmail().toLowerCase().strip();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            emailLogin,
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales incorrectas"));
        }

        Usuario usuario = usuarioService.buscarPorEmail(emailLogin)
                .orElseThrow(() ->
                        new IllegalStateException("Usuario no encontrado tras autenticación"));

        String token = jwtService.generarToken(
                usuario.getEmail(),
                usuario.getRol().name()
        );

        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                usuario.getEmail(),
                usuario.getRol().name(),
                jwtExpiration
        ));
    }

    // ==========================
    // DASHBOARD USUARIO
    // ==========================
    @PreAuthorize("hasRole('USUARIO')")
    @GetMapping("/usuario/dashboard")
    public ResponseEntity<?> userDashboard(Authentication authentication) {

        Usuario usuario = resolverUsuario(authentication);

        return ResponseEntity.ok(Map.of(
                "usuario", usuario,
                "topRecomendaciones",
                dashboardService.obtenerTopRecomendacionesPorProyecto(usuario.getId(), 3),
                "roadmap",
                dashboardService.obtenerRoadmap(usuario.getId()),
                "totalRecomendaciones",
                dashboardService.contarTotalRecomendaciones(usuario.getId())
        ));
    }

    // ==========================
    // REDIRECCIÓN LÓGICA POR ROL (modo API)
    // ==========================
    @GetMapping("/auth/default")
    public ResponseEntity<?> defaultAfterLogin(Authentication authentication) {

        if (authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {

            return ResponseEntity.ok(Map.of("redirect", "/api/admin/dashboard"));
        }

        if (authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_USUARIO"))) {

            return ResponseEntity.ok(Map.of("redirect", "/api/usuario/dashboard"));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Rol no autorizado"));
    }

    // ==========================
    // MÉTODO AUXILIAR
    // ==========================
    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado: " + authentication.getName()));
    }
}