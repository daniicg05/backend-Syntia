package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class BdnsEnrichmentService {

    public enum EstadoEnriquecimiento { INACTIVO, EN_PROGRESO, COMPLETADO, ERROR, CANCELADO }

    public record EstadoJob(
            EstadoEnriquecimiento estado,
            long procesados,
            long enriquecidos,
            long errores,
            long total,
            Instant iniciadoEn,
            Instant finalizadoEn
    ) {}

    private final AtomicBoolean activo = new AtomicBoolean(false);
    private final AtomicBoolean cancelado = new AtomicBoolean(false);
    private volatile EstadoJob estadoActual = new EstadoJob(
            EstadoEnriquecimiento.INACTIVO, 0, 0, 0, 0, null, null);

    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaRepository convocatoriaRepository;
    private final BdnsEnrichmentExecutor enrichmentExecutor;

    @Value("${bdns.enrichment.batch-size:100}")
    private int batchSize;

    @Value("${bdns.enrichment.delay-ms:300}")
    private long delayMs;

    public BdnsEnrichmentService(BdnsClientService bdnsClientService,
                                  ConvocatoriaRepository convocatoriaRepository,
                                  BdnsEnrichmentExecutor enrichmentExecutor) {
        this.bdnsClientService = bdnsClientService;
        this.convocatoriaRepository = convocatoriaRepository;
        this.enrichmentExecutor = enrichmentExecutor;
    }

    public boolean iniciar() {
        if (!activo.compareAndSet(false, true)) return false;
        cancelado.set(false);
        long total = convocatoriaRepository.countByNumeroConvocatoriaIsNotNull();
        estadoActual = new EstadoJob(EstadoEnriquecimiento.EN_PROGRESO, 0, 0, 0, total, Instant.now(), null);
        log.info("Enriquecimiento BDNS iniciado — total con numConv: {}", total);
        enrichmentExecutor.ejecutar(this::ejecutarEnriquecimiento);
        return true;
    }

    public boolean cancelar() {
        if (!activo.get()) return false;
        cancelado.set(true);
        return true;
    }

    public EstadoJob obtenerEstado() {
        return estadoActual;
    }

    void ejecutarEnriquecimiento() {
        long lastId = 0L;
        long procesados = 0, enriquecidos = 0, errores = 0;
        long total = estadoActual.total();
        Instant inicio = estadoActual.iniciadoEn();

        try {
            while (!cancelado.get()) {
                List<Convocatoria> batch = convocatoriaRepository
                        .findEnriquecimientoBatch(lastId, PageRequest.of(0, batchSize));

                if (batch.isEmpty()) break;

                for (Convocatoria c : batch) {
                    if (cancelado.get()) break;
                    lastId = c.getId();
                    try {
                        ConvocatoriaDTO dto = new ConvocatoriaDTO();
                        dto.setNumeroConvocatoria(c.getNumeroConvocatoria());
                        bdnsClientService.enriquecerConDetalle(dto);

                        boolean cambios = false;
                        if (dto.getPresupuesto() != null && c.getPresupuesto() == null) {
                            c.setPresupuesto(dto.getPresupuesto()); cambios = true;
                        }
                        if (dto.getAbierto() != null && c.getAbierto() == null) {
                            c.setAbierto(dto.getAbierto()); cambios = true;
                        }
                        if (dto.getFinalidad() != null && c.getFinalidad() == null) {
                            c.setFinalidad(dto.getFinalidad()); cambios = true;
                        }
                        if (dto.getFechaInicio() != null && c.getFechaInicio() == null) {
                            c.setFechaInicio(dto.getFechaInicio()); cambios = true;
                        }
                        if (dto.getFechaCierre() != null && c.getFechaCierre() == null) {
                            c.setFechaCierre(dto.getFechaCierre()); cambios = true;
                        }
                        if (dto.getTextoCompleto() != null && c.getTextoCompleto() == null) {
                            c.setTextoCompleto(dto.getTextoCompleto()); cambios = true;
                        }
                        // Sector: siempre sobreescribir con el dato real NACE (más preciso que la inferencia por palabras clave)
                        if (dto.getSector() != null) {
                            c.setSector(dto.getSector()); cambios = true;
                        }

                        if (cambios) {
                            convocatoriaRepository.save(c);
                            enriquecidos++;
                        }
                        procesados++;

                        if (delayMs > 0) Thread.sleep(delayMs);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        errores++;
                        procesados++;
                        log.warn("Enriquecimiento: error en id={}: {}", c.getId(), e.getMessage());
                    }

                    if (procesados % 500 == 0) {
                        estadoActual = new EstadoJob(EstadoEnriquecimiento.EN_PROGRESO,
                                procesados, enriquecidos, errores, total, inicio, null);
                        log.info("Enriquecimiento: {}/{} procesadas, {} enriquecidas, {} errores",
                                procesados, total, enriquecidos, errores);
                    }
                }
            }

            EstadoEnriquecimiento estadoFinal = cancelado.get()
                    ? EstadoEnriquecimiento.CANCELADO
                    : EstadoEnriquecimiento.COMPLETADO;
            estadoActual = new EstadoJob(estadoFinal, procesados, enriquecidos, errores, total, inicio, Instant.now());
            log.info("Enriquecimiento {}: {}/{} procesadas, {} enriquecidas", estadoFinal, procesados, total, enriquecidos);

        } catch (Exception e) {
            estadoActual = new EstadoJob(EstadoEnriquecimiento.ERROR,
                    procesados, enriquecidos, errores, total, inicio, Instant.now());
            log.error("Enriquecimiento fallido: {}", e.getMessage());
        } finally {
            activo.set(false);
        }
    }
}
