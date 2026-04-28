package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class CatalogoJobService {

    public enum Estado { INACTIVO, EN_CURSO, COMPLETADO, FALLIDO, CANCELADO }

    public record EstadoJob(
            Estado estado,
            String fase,
            int totalRegistros,
            LocalDateTime iniciadoEn,
            LocalDateTime finalizadoEn,
            String error,
            CatalogoImportService.ResultadoCatalogos resultado
    ) {}

    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private final AtomicBoolean cancelado = new AtomicBoolean(false);
    private final AtomicReference<EstadoJob> estadoActual =
            new AtomicReference<>(new EstadoJob(Estado.INACTIVO, null, 0, null, null, null, null));

    private final CatalogoImportService catalogoImportService;

    public CatalogoJobService(CatalogoImportService catalogoImportService) {
        this.catalogoImportService = catalogoImportService;
    }

    public EstadoJob obtenerEstado() {
        return estadoActual.get();
    }

    public boolean cancelar() {
        if (!enCurso.get()) return false;
        cancelado.set(true);
        log.info("Catalogo job: cancelacion solicitada");
        return true;
    }

    public boolean iniciar() {
        if (!enCurso.compareAndSet(false, true)) {
            log.warn("Ya hay un job de catalogos en curso");
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
            CatalogoImportService.ResultadoCatalogos resultado = catalogoImportService.importarTodos(
                    fase -> estadoActual.set(new EstadoJob(Estado.EN_CURSO, fase, 0, inicio, null, null, null)),
                    cancelado::get
            );
            int total = total(resultado);
            if (cancelado.get()) {
                estadoActual.set(new EstadoJob(Estado.CANCELADO, null, total, inicio, LocalDateTime.now(), "Cancelado por el usuario", resultado));
                log.info("Catalogo job cancelado: {} registros parciales", total);
            } else {
                estadoActual.set(new EstadoJob(Estado.COMPLETADO, null, total, inicio, LocalDateTime.now(), null, resultado));
                log.info("Catalogo job completado: {} registros totales", total);
            }
        } catch (Exception e) {
            log.error("Catalogo job FALLIDO: {}", e.getMessage(), e);
            estadoActual.set(new EstadoJob(Estado.FALLIDO, null, 0, inicio, LocalDateTime.now(), e.getMessage(), null));
        } finally {
            enCurso.set(false);
            cancelado.set(false);
        }
    }

    private int total(CatalogoImportService.ResultadoCatalogos res) {
        if (res == null) return 0;
        return res.finalidades() + res.instrumentos() + res.beneficiarios()
                + res.actividades() + res.reglamentos() + res.objetivos()
                + res.sectores() + res.organos();
    }
}
