package com.syntia.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Servicio para la generación, validación y extracción de información de tokens JWT.
 * Diseñado para API REST con autenticación stateless.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * Genera un token JWT para un usuario autenticado.
     *
     * @param email email del usuario (subject del token)
     * @param rol   rol del usuario
     * @return token JWT firmado
     */
    public String generarToken(String email, String rol) {
        return Jwts.builder()
                .subject(email)
                .claims(Map.of("rol", rol))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrae el email (subject) del token.
     * Si el token es inválido, lanza excepción.
     *
     * @param token token JWT
     * @return email del usuario
     * @throws ExpiredJwtException si el token expiró
     * @throws SignatureException si la firma no es válida
     * @throws MalformedJwtException si el token está malformado
     */
    public String extraerUsername(String token) {
        return extraerClaim(token, Claims::getSubject);
    }

    /**
     * Extrae el rol del token.
     * Si el token es inválido, lanza excepción.
     *
     * @param token token JWT
     * @return rol del usuario (o null si no está presente)
     */
    public String extraerRol(String token) {
        return extraerClaim(token, claims -> claims.get("rol", String.class));
    }

    /**
     * Valida si el token es válido (firma correcta y no expirado).
     * Retorna false sin lanzar excepciones (seguro para filtros).
     *
     * @param token    token JWT
     * @param username email del usuario a verificar
     * @return true si el token es válido, false en caso contrario
     */
    public boolean validarToken(String token, String username) {
        try {
            final String tokenUsername = extraerUsername(token);
            return tokenUsername.equals(username) && !isTokenExpirado(token);
        } catch (ExpiredJwtException | SignatureException | MalformedJwtException |
                 UnsupportedJwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extrae todos los claims del token de forma segura (sin lanzar excepciones).
     * Útil para validaciones en filtros.
     *
     * @param token token JWT
     * @return Claims si el token es válido, null en caso contrario
     */
    public Claims extraerTodosClaimsSeguro(String token) {
        try {
            return extraerTodosClaims(token);
        } catch (ExpiredJwtException | SignatureException | MalformedJwtException |
                 UnsupportedJwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Extrae un claim específico del token.
     * Si el token es inválido, propaga la excepción.
     */
    private <T> T extraerClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extraerTodosClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extrae todos los claims del token (valida firma y estructura).
     * Lanza excepciones específicas si el token es inválido.
     *
     * @throws ExpiredJwtException si el token expiró
     * @throws SignatureException si la firma no es válida
     * @throws MalformedJwtException si el token está malformado
     */
    private Claims extraerTodosClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Comprueba si el token ha expirado.
     * Si el token es inválido, lanza excepción.
     */
    private boolean isTokenExpirado(String token) {
        return extraerClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Obtiene la clave de firma a partir del secret configurado.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}