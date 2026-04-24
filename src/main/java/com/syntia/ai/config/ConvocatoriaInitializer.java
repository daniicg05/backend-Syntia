package com.syntia.ai.config;

import com.syntia.ai.service.ConvocatoriaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConvocatoriaInitializer {

    private final ConvocatoriaService convocatoriaService;

    @Value("${convocatorias.startup.fix-legacy-urls:false}")
    private boolean fixLegacyUrlsOnStartup;

    @Value("${convocatorias.startup.check-empty-table:false}")
    private boolean checkEmptyTableOnStartup;

    public ConvocatoriaInitializer(ConvocatoriaService convocatoriaService) {
        this.convocatoriaService = convocatoriaService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cargarConvocatoriasIniciales() {
        // La corrección masiva puede ser costosa; se deja desactivada por defecto y se habilita por propiedad.
        if (fixLegacyUrlsOnStartup) {
            int urlsCorregidas = convocatoriaService.corregirUrlsAntiguas();
            if (urlsCorregidas > 0) {
                log.info("URLs corregidas en BD: {} convocatorias actualizadas (/convocatoria/ → /convocatorias/)", urlsCorregidas);
            }
        }

        if (!checkEmptyTableOnStartup) {
            return;
        }

        long total = convocatoriaService.contarTodas();
        if (total > 0) {
            log.info("Ya existen {} convocatorias en BD — no se importan nuevas al arrancar.", total);
            return;
        }

        log.info("Tabla de convocatorias vacía — importación automática desactivada. Usa el panel de administración para lanzar el ETL.");
    }
}
