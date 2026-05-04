package com.syntia.ai.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de rate limiting para operaciones costosas (análisis IA, búsqueda BDNS).
 * Previene el abuso del motor de análisis limitando la frecuencia por usuario y proyecto.
 * <p>
 * Implementación stateful en memoria: se reinicia con cada despliegue.
 * Para producción multiinstancia considerar Redis como backend.
 */
@Service
public class RateLimitService {

    /** Tiempo mínimo entre búsquedas BDNS para el mismo proyecto (ms). */
    private static final long COOLDOWN_BUSQUEDA_MS = 30_000L;

    /** Tiempo mínimo entre análisis IA para el mismo proyecto (ms). */
    private static final long COOLDOWN_STREAM_MS = 60_000L;

    private final ConcurrentHashMap<String, Instant> ultimaBusqueda = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> ultimoStream   = new ConcurrentHashMap<>();

    // ── Búsqueda BDNS ────────────────────────────────────────────────────────

    public boolean puedeBuscar(Long usuarioId, Long proyectoId) {
        return transcurrido(ultimaBusqueda, clave(usuarioId, proyectoId), COOLDOWN_BUSQUEDA_MS);
    }

    public void registrarBusqueda(Long usuarioId, Long proyectoId) {
        ultimaBusqueda.put(clave(usuarioId, proyectoId), Instant.now());
    }

    public long segundosRestantesBusqueda(Long usuarioId, Long proyectoId) {
        return segundosRestantes(ultimaBusqueda, clave(usuarioId, proyectoId), COOLDOWN_BUSQUEDA_MS);
    }

    // ── Análisis IA ──────────────────────────────────────────────────────────

    public boolean puedeAnalizar(Long usuarioId, Long proyectoId) {
        return transcurrido(ultimoStream, clave(usuarioId, proyectoId), COOLDOWN_STREAM_MS);
    }

    public void registrarAnalisis(Long usuarioId, Long proyectoId) {
        ultimoStream.put(clave(usuarioId, proyectoId), Instant.now());
    }

    public long segundosRestantesAnalisis(Long usuarioId, Long proyectoId) {
        return segundosRestantes(ultimoStream, clave(usuarioId, proyectoId), COOLDOWN_STREAM_MS);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String clave(Long usuarioId, Long proyectoId) {
        return usuarioId + ":" + proyectoId;
    }

    private boolean transcurrido(ConcurrentHashMap<String, Instant> mapa, String clave, long cooldownMs) {
        Instant ultimo = mapa.get(clave);
        return ultimo == null || Duration.between(ultimo, Instant.now()).toMillis() >= cooldownMs;
    }

    private long segundosRestantes(ConcurrentHashMap<String, Instant> mapa, String clave, long cooldownMs) {
        Instant ultimo = mapa.get(clave);
        if (ultimo == null) return 0;
        long elapsed = Duration.between(ultimo, Instant.now()).toMillis();
        return Math.max(0, (cooldownMs - elapsed) / 1000);
    }
}
