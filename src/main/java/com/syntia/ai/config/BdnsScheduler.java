package com.syntia.ai.config;

import com.syntia.ai.service.BdnsImportJobService;
import com.syntia.ai.service.ModoImportacion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada que lanza la importación masiva de BDNS automáticamente
 * cada 6 meses. El cron es configurable vía {@code bdns.scheduler.cron}
 * en application.properties.
 * <p>
 * Valor por defecto: día 1 de enero y julio a las 3:00 AM.
 */
@Slf4j
@Component
public class BdnsScheduler {

    private final BdnsImportJobService bdnsImportJobService;

    public BdnsScheduler(BdnsImportJobService bdnsImportJobService) {
        this.bdnsImportJobService = bdnsImportJobService;
    }

    @Scheduled(cron = "${bdns.scheduler.cron:0 0 3 1 1,7 *}")
    public void importacionAutomatica() {
        log.info("Scheduler BDNS: lanzando importación masiva automática...");
        boolean iniciado = bdnsImportJobService.iniciar(ModoImportacion.FULL, -1);
        if (!iniciado) {
            log.warn("Scheduler BDNS: ya había un job en curso, se omite esta ejecución.");
        } else {
            log.info("Scheduler BDNS: job iniciado correctamente.");
        }
    }
}