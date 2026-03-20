package com.syntia.ai.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT para API REST.
 * Intercepta requests y autentica usuarios mediante token Bearer.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // ==============================
        // 🔓 RUTAS PÚBLICAS (NO JWT)
        // ==============================
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ==============================
        // 🔐 TOKEN EXTRACTION
        // ==============================
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        if (token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // ==============================
        // 🔍 VALIDACIÓN TOKEN
        // ==============================
        Claims claims = jwtService.extraerTodosClaimsSeguro(token);

        if (claims == null) {
            log.debug("Token inválido o expirado en: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        final String username = claims.getSubject();

        if (username != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            if (jwtService.validarToken(token, username)) {

                String rol = claims.get("rol", String.class);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority(
                                        "ROLE_" + (rol != null ? rol : "USUARIO")
                                ))
                        );

                auth.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("JWT autenticado correctamente: {}", username);
            } else {
                log.debug("Token JWT inválido para usuario: {}", username);
            }
        }

        filterChain.doFilter(request, response);
    }

    // ==============================
    // 🧠 LISTA DE RUTAS PÚBLICAS
    // ==============================
    private boolean isPublicPath(String path) {

        return path.startsWith("/api/auth/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/error");
    }
}