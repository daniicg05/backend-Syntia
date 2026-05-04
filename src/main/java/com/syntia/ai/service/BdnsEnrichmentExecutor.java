package com.syntia.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BdnsEnrichmentExecutor {

    @Async
    public void ejecutar(Runnable tarea) {
        try {
            tarea.run();
        } catch (Exception e) {
            log.error("BdnsEnrichmentExecutor: error inesperado: {}", e.getMessage(), e);
        }
    }
}
