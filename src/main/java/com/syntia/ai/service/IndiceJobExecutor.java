package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Ejecutor asíncrono de la construcción de índices catálogo ↔ convocatoria.
 * Clase separada de {@link IndiceJobService} para que {@code @Async}
 * funcione correctamente a través del proxy de Spring.
 */
@Slf4j
@Service
public class IndiceJobExecutor {

    private final IndiceConvocatoriaService indiceConvocatoriaService;

    public IndiceJobExecutor(IndiceConvocatoriaService indiceConvocatoriaService) {
        this.indiceConvocatoriaService = indiceConvocatoriaService;
    }

    @Async
    public void ejecutar(Consumer<String> onFase,
                         Consumer<IndiceConvocatoriaService.ResultadoIndices> onCompletado,
                         BiConsumer<String, IndiceConvocatoriaService.ResultadoIndices> onCancelado,
                         BiConsumer<String, IndiceConvocatoriaService.ResultadoIndices> onFallido,
                         AtomicBoolean cancelado) {
        IndiceConvocatoriaService.ResultadoIndices resultado = null;
        try {
            log.info("=== IndiceJobExecutor: arrancando construcción de índices ===");
            resultado = indiceConvocatoriaService.construirTodos(onFase, cancelado);
            if (cancelado.get()) {
                log.info("=== IndiceJobExecutor: construcción cancelada ===");
                onCancelado.accept("Cancelado por el usuario", resultado);
            } else {
                log.info("=== IndiceJobExecutor: construcción completada ===");
                onCompletado.accept(resultado);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("=== IndiceJobExecutor: construcción interrumpida ===");
            onFallido.accept("Construcción interrumpida", resultado);
        } catch (Exception e) {
            log.error("=== IndiceJobExecutor: construcción FALLIDA: {} ===", e.getMessage(), e);
            onFallido.accept(e.getMessage(), resultado);
        }
    }
}
