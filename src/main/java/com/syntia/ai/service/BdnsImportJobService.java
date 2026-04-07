package com.syntia.ai.service;

import com.syntia.ai.model.dto.ConvocatoriaDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio que ejecuta la importación masiva de toda la BDNS como job asíncrono.
 * <p>
 * Itera página a página (50 registros c/u) hasta agotar los resultados.
 * Usa un AtomicBoolean como guard para evitar dos jobs simultáneos.
 * El estado se expone en tiempo real a través de {@link #obtenerEstado()}.
 */
@Slf4j
@Service
public class BdnsImportJobService {

    private static final int TAM_PAGINA = 50;
    private static final int LOG_CADA_PAGINAS = 10;

    public enum EstadoImportacion { INACTIVO, EN_CURSO, COMPLETADO, FALLIDO }

    public record EstadoJob(
            EstadoImportacion estado,
            int paginaActual,
            int registrosImportados,
            LocalDateTime iniciadoEn,
            LocalDateTime finalizadoEn,
            String error
    ) {}

    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private final AtomicReference<EstadoJob> estadoActual =
            new AtomicReference<>(new EstadoJob(EstadoImportacion.INACTIVO, 0, 0, null, null, null));

    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaService convocatoriaService;

    public BdnsImportJobService(BdnsClientService bdnsClientService,
                                ConvocatoriaService convocatoriaService) {
        this.bdnsClientService = bdnsClientService;
        this.convocatoriaService = convocatoriaService;
    }

    /** Devuelve el estado actual del job sin iniciar ninguno. */
    public EstadoJob obtenerEstado() {
        return estadoActual.get();
    }

    /**
     * Lanza la importación completa como job asíncrono.
     *
     * @return true si se inició correctamente, false si ya había uno en curso
     */
    public boolean iniciar() {
        if (!enCurso.compareAndSet(false, true)) {
            log.warn("Ya hay una importación BDNS en curso — petición ignorada");
            return false;
        }
        ejecutarImportacion();
        return true;
    }

    @Async
    protected void ejecutarImportacion() {
        log.info("=== Importación masiva BDNS iniciada ===");
        LocalDateTime inicio = LocalDateTime.now();
        estadoActual.set(new EstadoJob(EstadoImportacion.EN_CURSO, 0, 0, inicio, null, null));

        int pag = 0;
        int totalNuevos = 0;
        try {
            while (true) {
                List<ConvocatoriaDTO> pagina = bdnsClientService.importar(pag, TAM_PAGINA);

                if (pagina.isEmpty()) {
                    log.info("BDNS importación: página {} vacía — fin de datos", pag);
                    break;
                }

                int nuevas = convocatoriaService.persistirNuevas(pagina);
                totalNuevos += nuevas;

                if (pag % LOG_CADA_PAGINAS == 0) {
                    log.info("BDNS importación: página {} — {} registros nuevos acumulados", pag, totalNuevos);
                }

                estadoActual.set(new EstadoJob(EstadoImportacion.EN_CURSO, pag, totalNuevos, inicio, null, null));

                if (pagina.size() < TAM_PAGINA) {
                    log.info("BDNS importación: página {} con {} resultados (< {}) — fin de datos",
                            pag, pagina.size(), TAM_PAGINA);
                    break;
                }
                pag++;
            }

            log.info("=== Importación masiva BDNS completada: {} nuevos en {} páginas ===", totalNuevos, pag + 1);
            estadoActual.set(new EstadoJob(EstadoImportacion.COMPLETADO, pag, totalNuevos,
                    inicio, LocalDateTime.now(), null));

        } catch (Exception e) {
            log.error("=== Importación masiva BDNS FALLIDA en página {}: {} ===", pag, e.getMessage(), e);
            estadoActual.set(new EstadoJob(EstadoImportacion.FALLIDO, pag, totalNuevos,
                    inicio, LocalDateTime.now(), e.getMessage()));
        } finally {
            enCurso.set(false);
        }
    }
}