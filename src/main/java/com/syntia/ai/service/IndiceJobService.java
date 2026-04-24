package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gestiona el ciclo de vida del job asíncrono de construcción de índices BDNS (Fase 2).
 * Patrón análogo a BdnsImportJobService para evitar el bloqueo del hilo HTTP.
 */
@Slf4j
@Service
public class IndiceJobService {

    public enum Estado { INACTIVO, EN_CURSO, COMPLETADO, FALLIDO }

    public record EstadoJob(
            Estado estado,
            String fase,
            int totalRegistros,
            LocalDateTime iniciadoEn,
            LocalDateTime finalizadoEn,
            String error,
            IndiceConvocatoriaService.ResultadoIndices resultado
    ) {}

    private final AtomicBoolean enCurso   = new AtomicBoolean(false);
    private final AtomicBoolean cancelado = new AtomicBoolean(false);
    private final AtomicReference<EstadoJob> estadoActual =
            new AtomicReference<>(new EstadoJob(Estado.INACTIVO, null, 0, null, null, null, null));

    private final IndiceConvocatoriaService indiceConvocatoriaService;
    private final CatalogoImportService catalogoImportService;

    public IndiceJobService(IndiceConvocatoriaService indiceConvocatoriaService,
                            CatalogoImportService catalogoImportService) {
        this.indiceConvocatoriaService = indiceConvocatoriaService;
        this.catalogoImportService = catalogoImportService;
    }

    public EstadoJob obtenerEstado() { return estadoActual.get(); }

    public boolean cancelar() {
        if (!enCurso.get()) return false;
        cancelado.set(true);
        log.info("Índice job: cancelación solicitada");
        return true;
    }

    /**
     * Lanza la construcción de índices en segundo plano.
     * @return false si ya hay uno en curso
     */
    public boolean iniciar() {
        if (!enCurso.compareAndSet(false, true)) {
            log.warn("Ya hay un job de índices en curso — petición ignorada");
            return false;
        }
        cancelado.set(false);
        LocalDateTime inicio = LocalDateTime.now();
        estadoActual.set(new EstadoJob(Estado.EN_CURSO, "Iniciando...", 0, inicio, null, null, null));
        ejecutarAsync(inicio);
        return true;
    }

    @Async
    void ejecutarAsync(LocalDateTime inicio) {
        try {
            // Fase 1: importar catálogos siempre antes de construir índices
            // para garantizar que cat_* están poblados aunque el usuario haya
            // pulsado "Construir índices" sin pulsar antes "Importar catálogos".
            catalogoImportService.importarTodos(
                    fase -> estadoActual.set(new EstadoJob(Estado.EN_CURSO, fase, 0, inicio, null, null, null))
            );
            if (cancelado.get()) {
                estadoActual.set(new EstadoJob(Estado.FALLIDO, null, 0, inicio, LocalDateTime.now(), "Cancelado", null));
                return;
            }

            // Fase 2: construir índices
            IndiceConvocatoriaService.ResultadoIndices res = indiceConvocatoriaService.construirTodos(
                    fase -> estadoActual.set(new EstadoJob(Estado.EN_CURSO, fase, 0, inicio, null, null, null)),
                    cancelado
            );
            int total = res.finalidades() + res.instrumentos() + res.beneficiarios()
                      + res.organos() + res.tiposAdmin()
                      + res.actividades() + res.reglamentos() + res.objetivos() + res.sectores();
            estadoActual.set(new EstadoJob(Estado.COMPLETADO, null, total, inicio, LocalDateTime.now(), null, res));
            log.info("Índice job completado: {} registros totales", total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            estadoActual.set(new EstadoJob(Estado.FALLIDO, null, 0, inicio, LocalDateTime.now(), "Interrumpido", null));
        } catch (Exception e) {
            log.error("Índice job fallido: {}", e.getMessage(), e);
            estadoActual.set(new EstadoJob(Estado.FALLIDO, null, 0, inicio, LocalDateTime.now(), e.getMessage(), null));
        } finally {
            enCurso.set(false);
            cancelado.set(false);
        }
    }
}
