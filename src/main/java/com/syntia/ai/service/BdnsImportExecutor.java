package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Ejecutor asíncrono de la importación masiva BDNS.
 * <p>
 * Clase separada de {@link BdnsImportJobService} para que {@code @Async}
 * funcione correctamente a través del proxy de Spring (evita el problema
 * de self-invocation donde {@code this.metodo()} bypasea el proxy AOP).
 */
@Slf4j
@Service
public class BdnsImportExecutor {

    private final BdnsImportEstrategiaService estrategiaService;

    public BdnsImportExecutor(BdnsImportEstrategiaService estrategiaService) {
        this.estrategiaService = estrategiaService;
    }

    /**
     * Lanza la importación completa en un hilo del pool async de Spring.
     *
     * @param onProgreso  callback(ejeActual, totalNuevos) — llamado tras cada página
     * @param onCompletado callback(totalNuevos) — llamado al terminar con éxito
     * @param onFallido   callback(mensajeError, totalNuevos) — llamado si hay excepción
     */
    @Async
    public void ejecutar(BiConsumer<String, Integer> onProgreso,
                         Consumer<Integer> onCompletado,
                         BiConsumer<String, Integer> onFallido,
                         ModoImportacion modo,
                         java.util.concurrent.atomic.AtomicBoolean cancelado,
                         long delayMsOverride) {
        int total = 0;
        try {
            log.info("=== BdnsImportExecutor: arrancando importación masiva — modo={} delayMs={} ===", modo, delayMsOverride);
            total = estrategiaService.importarTodo(onProgreso, modo, cancelado, delayMsOverride);
            log.info("=== BdnsImportExecutor: importación completada — {} nuevos ===", total);
            onCompletado.accept(total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("=== BdnsImportExecutor: importación interrumpida ===");
            onFallido.accept("Importación interrumpida", total);
        } catch (Exception e) {
            log.error("=== BdnsImportExecutor: importación FALLIDA: {} ===", e.getMessage(), e);
            onFallido.accept(e.getMessage(), total);
        }
    }
}