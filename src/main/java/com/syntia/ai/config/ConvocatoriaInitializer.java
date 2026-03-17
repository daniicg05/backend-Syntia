package com.syntia.ai.config;

import com.syntia.mvp.service.ConvocatoriaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConvocatoriaInitializer {

    private static final int PAGINAS_INICIALES = 3;
    private static final int TAM_PAGINA = 50;

    private final ConvocatoriaService convocatoriaService;

    public ConvocatoriaInitializer(ConvocatoriaService convocatoriaService) {
        this.convocatoriaService = convocatoriaService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cargarConvocatoriasIniciales() {
        // 1. Corregir URLs antiguas /convocatoria/ → /convocatorias/ en toda la BD
        int urlsCorregidas = convocatoriaService.corregirUrlsAntiguas();
        if (urlsCorregidas > 0) {
            log.info("URLs corregidas en BD: {} convocatorias actualizadas (/convocatoria/ → /convocatorias/)", urlsCorregidas);
        }

        // 2. Importar si la tabla está vacía
        long total = convocatoriaService.obtenerTodas().size();
        if (total > 0) {
            log.info("Ya existen {} convocatorias en BD — no se importan nuevas al arrancar.", total);
            return;
        }

        log.info("Tabla de convocatorias vacía — importando desde la API real de BDNS...");
        int totalNuevas = 0;
        try {
            for (int pag = 0; pag < PAGINAS_INICIALES; pag++) {
                int nuevas = convocatoriaService.importarDesdeBdns(pag, TAM_PAGINA);
                totalNuevas += nuevas;
                log.info("BDNS página {}: {} convocatorias nuevas importadas", pag, nuevas);
            }
            log.info("Importación inicial completada: {} convocatorias reales cargadas desde BDNS.", totalNuevas);
        } catch (Exception e) {
            log.warn("Error importando desde BDNS (se importaron {} antes del error): {}",
                    totalNuevas, e.getMessage());
        }
    }
}
