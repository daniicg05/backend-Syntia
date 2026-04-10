package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gestiona el ciclo de vida del job de importación masiva BDNS.
 * <p>
 * Delega la ejecución asíncrona real a {@link BdnsImportExecutor} para que
 * {@code @Async} funcione correctamente a través del proxy de Spring.
 * El estado se expone en tiempo real mediante {@link #obtenerEstado()}.
 */
@Slf4j
@Service
public class BdnsImportJobService {

    public enum EstadoImportacion { INACTIVO, EN_CURSO, COMPLETADO, FALLIDO }

    public record EstadoJob(
            EstadoImportacion estado,
            int registrosImportados,
            String ejeActual,
            LocalDateTime iniciadoEn,
            LocalDateTime finalizadoEn,
            String error,
            ModoImportacion modo
    ) {}

    private final AtomicBoolean enCurso    = new AtomicBoolean(false);
    private final AtomicBoolean cancelado  = new AtomicBoolean(false);
    private final AtomicReference<EstadoJob> estadoActual =
            new AtomicReference<>(new EstadoJob(EstadoImportacion.INACTIVO, 0, null, null, null, null, null));

    /** Solicita la cancelación del job en curso. No-op si no hay ninguno. */
    public boolean cancelar() {
        if (!enCurso.get()) return false;
        cancelado.set(true);
        log.info("BDNS import: cancelación solicitada");
        return true;
    }

    private final BdnsImportExecutor bdnsImportExecutor;

    public BdnsImportJobService(BdnsImportExecutor bdnsImportExecutor) {
        this.bdnsImportExecutor = bdnsImportExecutor;
    }

    /** Devuelve el estado actual del job sin iniciar ninguno. */
    public EstadoJob obtenerEstado() {
        return estadoActual.get();
    }

    /**
     * Lanza la importación completa como job asíncrono.
     *
     * @param modo        FULL o INCREMENTAL
     * @param delayMsOverride ms de espera entre páginas; -1 usa el valor de configuración; 0 = turbo (sin delay)
     * @return true si se inició correctamente, false si ya había uno en curso
     */
    public boolean iniciar(ModoImportacion modo, long delayMsOverride) {
        if (!enCurso.compareAndSet(false, true)) {
            log.warn("Ya hay una importación BDNS en curso — petición ignorada");
            return false;
        }
        cancelado.set(false);
        LocalDateTime inicio = LocalDateTime.now();
        estadoActual.set(new EstadoJob(EstadoImportacion.EN_CURSO, 0, "Iniciando...", inicio, null, null, modo));

        bdnsImportExecutor.ejecutar(
                (eje, total) -> estadoActual.set(new EstadoJob(
                        EstadoImportacion.EN_CURSO, total, eje, inicio, null, null, modo)),
                (total) -> {
                    estadoActual.set(new EstadoJob(
                            EstadoImportacion.COMPLETADO, total, null, inicio, LocalDateTime.now(), null, modo));
                    enCurso.set(false);
                    cancelado.set(false);
                },
                (error, total) -> {
                    estadoActual.set(new EstadoJob(
                            EstadoImportacion.FALLIDO, total, null, inicio, LocalDateTime.now(), error, modo));
                    enCurso.set(false);
                    cancelado.set(false);
                },
                modo,
                cancelado,
                delayMsOverride
        );
        return true;
    }
}