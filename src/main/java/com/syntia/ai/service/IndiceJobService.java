package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class IndiceJobService {

    public enum Estado { INACTIVO, EN_CURSO, COMPLETADO, FALLIDO, CANCELADO }

    public record EstadoJob(
            Estado estado,
            String fase,
            int totalRegistros,
            LocalDateTime iniciadoEn,
            LocalDateTime finalizadoEn,
            String error,
            IndiceConvocatoriaService.ResultadoIndices resultado
    ) {}

    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private final AtomicBoolean cancelado = new AtomicBoolean(false);
    private final AtomicReference<EstadoJob> estadoActual =
            new AtomicReference<>(new EstadoJob(Estado.INACTIVO, null, 0, null, null, null, null));

    private final IndiceJobExecutor indiceJobExecutor;

    public IndiceJobService(IndiceJobExecutor indiceJobExecutor) {
        this.indiceJobExecutor = indiceJobExecutor;
    }

    public EstadoJob obtenerEstado() {
        return estadoActual.get();
    }

    public boolean cancelar() {
        if (!enCurso.get()) return false;
        cancelado.set(true);
        log.info("Indice job: cancelacion solicitada");
        return true;
    }

    public boolean iniciar() {
        if (!enCurso.compareAndSet(false, true)) {
            log.warn("Ya hay un job de indices en curso");
            return false;
        }

        cancelado.set(false);
        LocalDateTime inicio = LocalDateTime.now();
        estadoActual.set(new EstadoJob(Estado.EN_CURSO, "Iniciando...", 0, inicio, null, null, null));

        indiceJobExecutor.ejecutar(
                fase -> estadoActual.set(new EstadoJob(Estado.EN_CURSO, fase, 0, inicio, null, null, null)),
                resultado -> {
                    int total = total(resultado);
                    estadoActual.set(new EstadoJob(Estado.COMPLETADO, null, total, inicio, LocalDateTime.now(), null, resultado));
                    enCurso.set(false);
                    cancelado.set(false);
                    log.info("Indice job completado: {} registros totales", total);
                },
                (mensaje, resultado) -> {
                    int total = total(resultado);
                    estadoActual.set(new EstadoJob(Estado.CANCELADO, null, total, inicio, LocalDateTime.now(), mensaje, resultado));
                    enCurso.set(false);
                    cancelado.set(false);
                    log.info("Indice job cancelado: {} registros parciales", total);
                },
                (error, resultado) -> {
                    int total = total(resultado);
                    estadoActual.set(new EstadoJob(Estado.FALLIDO, null, total, inicio, LocalDateTime.now(), error, resultado));
                    enCurso.set(false);
                    cancelado.set(false);
                },
                cancelado
        );

        return true;
    }

    private int total(IndiceConvocatoriaService.ResultadoIndices res) {
        if (res == null) return 0;
        return res.finalidades() + res.instrumentos() + res.beneficiarios()
                + res.organos() + res.tiposAdmin()
                + res.actividades() + res.reglamentos() + res.objetivos() + res.sectores();
    }
}
