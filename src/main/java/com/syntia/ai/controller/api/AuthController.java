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
import com.syntia.ai.service.EmailService;

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
    private final EmailService emailService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UsuarioService usuarioService,
                          DashboardService dashboardService, EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
        this.dashboardService = dashboardService;
        this.emailService = emailService;
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

        Usuario usuarioCreado;
        try {
            usuarioCreado = usuarioService.registrar(dto.getEmail(), dto.getPassword(), Rol.USUARIO);

            /**
             * Envía automáticamente el correo de verificación tras crear la cuenta.
             * Si falla el envío, se propaga IllegalStateException y lo gestiona
             * GlobalExceptionHandler.
             */
            emailService.sendVerificationEmail(
                    usuarioCreado.getEmail(),
                    usuarioCreado.getVerificationToken()
            );

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

        String token = jwtService.generarToken(usuarioCreado.getEmail(), usuarioCreado.getRol().name());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LoginResponseDTO(
                        token,
                        usuarioCreado.getEmail(),
                        usuarioCreado.getRol().name(),
                        jwtExpiration
                ));
    }

    /**
     * Verifica la cuenta de un usuario a partir de un token recibido por query param.
     *
     * <p>Ejemplo de uso:
     * <ul>
     *   <li>GET /api/auth/verify?token=abc123</li>
     * </ul>
     *
     * @param token token de verificación generado en el registro
     * @return mensaje de confirmación si la verificación fue exitosa
     */
    @GetMapping("/auth/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        usuarioService.verificarToken(token);
        return ResponseEntity.ok(Map.of("mensaje", "Cuenta verificada correctamente"));
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

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales incorrectas"));
        }

        Usuario usuario = usuarioService.buscarPorEmail(request.getEmail())
                .orElseThrow(() ->
                        new IllegalStateException("Usuario no encontrado tras autenticación"));

        /**
         * Bloquea el inicio de sesión hasta que la cuenta se verifique por token.
         * GlobalExceptionHandler transformará IllegalStateException en HTTP 409.
         */
        if (!usuario.isVerified()) {
            throw new IllegalStateException("Debes verificar tu email antes de iniciar sesión");
        }

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