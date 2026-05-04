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

    private final BdnsEtlJobCoordinator bdnsEtlJobCoordinator;
    private final IndiceJobExecutor indiceJobExecutor;
    private final IndiceConvocatoriaService indiceConvocatoriaService;

    public IndiceJobService(BdnsEtlJobCoordinator bdnsEtlJobCoordinator,
                            IndiceJobExecutor indiceJobExecutor,
                            IndiceConvocatoriaService indiceConvocatoriaService) {
        this.bdnsEtlJobCoordinator = bdnsEtlJobCoordinator;
        this.indiceJobExecutor = indiceJobExecutor;
        this.indiceConvocatoriaService = indiceConvocatoriaService;
    }

    public EstadoJob obtenerEstado() {
        return estadoActual.get();
    }

    public boolean estaEnCurso() {
        return enCurso.get() || bdnsEtlJobCoordinator.estaEnCurso(BdnsEtlJobCoordinator.Job.INDICES);
    }

    public boolean cancelar() {
        if (!enCurso.get()) return false;
        cancelado.set(true);
        log.info("Indice job: cancelacion solicitada");
        return true;
    }

    public boolean iniciar() {
        return iniciar(null);
    }

    public boolean iniciar(Integer limiteConvocatorias) {
        if (!bdnsEtlJobCoordinator.iniciar(BdnsEtlJobCoordinator.Job.INDICES)) {
            log.warn("Ya hay un job ETL BDNS en curso");
            return false;
        }

        if (!enCurso.compareAndSet(false, true)) {
            log.warn("Ya hay un job de indices en curso");
            bdnsEtlJobCoordinator.finalizar(BdnsEtlJobCoordinator.Job.INDICES);
            return false;
        }

        cancelado.set(false);
        LocalDateTime inicio = LocalDateTime.now();
        estadoActual.set(new EstadoJob(Estado.EN_CURSO, "Iniciando...", 0, inicio, null, null, null));

        indiceJobExecutor.ejecutar(
                fase -> estadoActual.set(new EstadoJob(Estado.EN_CURSO, fase, total(indiceConvocatoriaService.contarTodos()), inicio, null, null, null)),
                resultado -> {
                    int total = total(indiceConvocatoriaService.contarTodos());
                    estadoActual.set(new EstadoJob(Estado.COMPLETADO, null, total, inicio, LocalDateTime.now(), null, resultado));
                    enCurso.set(false);
                    cancelado.set(false);
                    bdnsEtlJobCoordinator.finalizar(BdnsEtlJobCoordinator.Job.INDICES);
                    log.info("Indice job completado: {} registros totales", total);
                },
                (mensaje, resultado) -> {
                    int total = total(indiceConvocatoriaService.contarTodos());
                    estadoActual.set(new EstadoJob(Estado.CANCELADO, null, total, inicio, LocalDateTime.now(), mensaje, resultado));
                    enCurso.set(false);
                    cancelado.set(false);
                    bdnsEtlJobCoordinator.finalizar(BdnsEtlJobCoordinator.Job.INDICES);
                    log.info("Indice job cancelado: {} registros parciales", total);
                },
                (error, resultado) -> {
                    int total = total(indiceConvocatoriaService.contarTodos());
                    estadoActual.set(new EstadoJob(Estado.FALLIDO, null, total, inicio, LocalDateTime.now(), error, resultado));
                    enCurso.set(false);
                    cancelado.set(false);
                    bdnsEtlJobCoordinator.finalizar(BdnsEtlJobCoordinator.Job.INDICES);
                },
                cancelado,
                limiteConvocatorias
        );

        return true;
    }

    private int total(IndiceConvocatoriaService.ResultadoIndices res) {
        if (res == null) return 0;
        return res.finalidades() + res.instrumentos() + res.beneficiarios()
                + res.organos() + res.regiones() + res.tiposAdmin()
                + res.actividades() + res.reglamentos() + res.objetivos() + res.sectores();
    }

    private int total(IndiceConvocatoriaService.ConteoIndices conteos) {
        if (conteos == null) return 0;
        long total = conteos.finalidades() + conteos.instrumentos() + conteos.beneficiarios()
                + conteos.organos() + conteos.regiones() + conteos.tiposAdmin()
                + conteos.actividades() + conteos.reglamentos() + conteos.objetivos() + conteos.sectores();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
