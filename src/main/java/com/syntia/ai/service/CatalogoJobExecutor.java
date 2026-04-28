package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
@Service
public class CatalogoJobExecutor {

    private final CatalogoImportService catalogoImportService;

    public CatalogoJobExecutor(CatalogoImportService catalogoImportService) {
        this.catalogoImportService = catalogoImportService;
    }

    @Async
    public void ejecutar(Consumer<String> onProgreso,
                         Consumer<CatalogoImportService.ResultadoCatalogos> onCompletado,
                         BiConsumer<String, CatalogoImportService.ResultadoCatalogos> onCancelado,
                         BiConsumer<String, CatalogoImportService.ResultadoCatalogos> onFallido,
                         AtomicBoolean cancelado) {
        CatalogoImportService.ResultadoCatalogos resultado = null;
        try {
            resultado = catalogoImportService.importarTodos(onProgreso, cancelado::get);
            if (cancelado.get()) {
                onCancelado.accept("Cancelado por el usuario", resultado);
            } else {
                onCompletado.accept(resultado);
            }
        } catch (Exception e) {
            log.error("Catalogo job fallido: {}", e.getMessage(), e);
            onFallido.accept(e.getMessage(), resultado);
        }
    }
}
