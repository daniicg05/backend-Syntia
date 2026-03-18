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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticación JWT para API REST.
 * Intercepta cada petición HTTP, extrae y valida el token del header Authorization,
 * y establece el contexto de seguridad de Spring Security.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Si no hay header Authorization o no empieza con "Bearer ", continuar sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        // Validar que el token no esté vacío
        if (token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extraer claims de forma segura (sin excepciones)
        Claims claims = jwtService.extraerTodosClaimsSeguro(token);
        if (claims == null) {
            log.debug("Token JWT inválido o expirado en petición a: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        final String username = claims.getSubject();

        // Si se extrajo el username y no hay autenticación previa en el contexto
        if (username != null && !username.isBlank() &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            // Validar el token contra el usuario
            if (jwtService.validarToken(token, username)) {
                String rol = claims.get("rol", String.class);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + (rol != null ? rol : "USUARIO")))
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Autenticación JWT establecida para usuario: {}", username);
            } else {
                log.debug("Validación de token JWT fallida para usuario: {}", username);
            }
        }

        filterChain.doFilter(request, response);
    }
}

