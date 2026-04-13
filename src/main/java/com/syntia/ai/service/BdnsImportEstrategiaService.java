package com.syntia.ai.service;

import com.syntia.ai.model.SyncLog;
import com.syntia.ai.model.SyncState;
import com.syntia.ai.model.dto.ResultadoPersistencia;
import com.syntia.ai.repository.SyncLogRepository;
import com.syntia.ai.repository.SyncStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Estrategia de importación masiva BDNS mediante paginación global.
 * <p>
 * La API de BDNS ignora los filtros nivel1/nivel2 — siempre devuelve el dataset
 * completo (~620k registros). Por ello se itera una única vez sin filtros,
 * usando los parámetros estándar Spring Data {@code page} y {@code size}.
 * La deduplicación por idBdns evita duplicados en reimportaciones.
 */
@Slf4j
@Service
public class BdnsImportEstrategiaService {

    private static final int TAM_PAGINA = 50;
    private static final String EJE_GLOBAL = "GLOBAL";

    @Value("${bdns.import.delay-ms:300}")
    private long delayMs;

    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaService convocatoriaService;
    private final SyncStateRepository syncStateRepo;
    private final SyncLogRepository syncLogRepo;

    public BdnsImportEstrategiaService(BdnsClientService bdnsClientService,
                                       ConvocatoriaService convocatoriaService,
                                       SyncStateRepository syncStateRepo,
                                       SyncLogRepository syncLogRepo) {
        this.bdnsClientService = bdnsClientService;
        this.convocatoriaService = convocatoriaService;
        this.syncStateRepo = syncStateRepo;
        this.syncLogRepo = syncLogRepo;
    }

    /**
     * Ejecuta la importación global de BDNS.
     *
     * @param onProgreso callback invocado tras cada página: (descripción, totalNuevosAcumulados)
     * @param modo       FULL reinicia desde página 0; INCREMENTAL reanuda desde la última ok
     * @return total de registros nuevos importados
     */
    public int importarTodo(BiConsumer<String, Integer> onProgreso,
                            ModoImportacion modo,
                            AtomicBoolean cancelado,
                            long delayMsOverride) throws InterruptedException {
        long efectiveDelayMs = delayMsOverride >= 0 ? delayMsOverride : this.delayMs;
        String ejecucionId = UUID.randomUUID().toString();
        log.info("BDNS import global: ejecucionId={} modo={} delayMs={}", ejecucionId, modo, efectiveDelayMs);

        SyncState syncState = syncStateRepo.findByEje(EJE_GLOBAL)
                .orElse(SyncState.builder().eje(EJE_GLOBAL).build());

        if (modo == ModoImportacion.INCREMENTAL && syncState.getEstado() == SyncState.Estado.COMPLETADO) {
            log.info("BDNS import global: ya COMPLETADO en modo INCREMENTAL, omitido");
            onProgreso.accept("GLOBAL [omitido]", 0);
            return 0;
        }

        int paginaInicio = 0;
        boolean esReanudacion = modo == ModoImportacion.INCREMENTAL
                && (syncState.getEstado() == SyncState.Estado.ERROR
                    || syncState.getEstado() == SyncState.Estado.EN_PROGRESO)
                && syncState.getUltimaPaginaOk() >= 0;
        if (esReanudacion) {
            paginaInicio = syncState.getUltimaPaginaOk() + 1;
            log.info("BDNS import global: reanudando desde pág. {} (última ok: {})",
                    paginaInicio, syncState.getUltimaPaginaOk());
        }

        syncState.setEstado(SyncState.Estado.EN_PROGRESO);
        syncState.setTsInicio(Instant.now());
        syncState.setTsUltimaCarga(null);
        if (modo == ModoImportacion.FULL || paginaInicio == 0) {
            syncState.setUltimaPaginaOk(-1);
            syncState.setRegistrosNuevos(0);
            syncState.setRegistrosActualizados(0);
        }
        syncState = syncStateRepo.save(syncState);

        int pag = paginaInicio;
        int nuevosTotal = 0;
        int maxPaginas = Integer.MAX_VALUE;

        try {
            while (pag <= maxPaginas && !cancelado.get()) {
                String progStr = maxPaginas == Integer.MAX_VALUE
                        ? "GLOBAL – pág. " + pag
                        : "GLOBAL – pág. " + pag + "/" + (maxPaginas + 1);
                onProgreso.accept(progStr, nuevosTotal);

                BdnsClientService.PaginaBdns pagina = bdnsClientService.importarPorEje(null, null, pag, TAM_PAGINA);

                if (pag == paginaInicio && pagina.totalElements() > 0) {
                    maxPaginas = (int) Math.ceil((double) pagina.totalElements() / TAM_PAGINA) - 1;
                    log.info("BDNS import global: totalElements={} → {} páginas máx.",
                            pagina.totalElements(), maxPaginas + 1);
                }

                if (pagina.items().isEmpty()) {
                    log.info("BDNS import global: completado — {} páginas, {} nuevos", pag, nuevosTotal);
                    break;
                }

                // Enriquecer cada convocatoria con datos del endpoint de detalle
                for (com.syntia.ai.model.dto.ConvocatoriaDTO dto : pagina.items()) {
                    bdnsClientService.enriquecerConDetalle(dto);
                }

                ResultadoPersistencia resultado = convocatoriaService.persistirNuevas(pagina.items());
                nuevosTotal += resultado.nuevas();

                syncState.setUltimaPaginaOk(pag);
                syncState.setRegistrosNuevos(syncState.getRegistrosNuevos() + resultado.nuevas());
                syncState.setRegistrosActualizados(syncState.getRegistrosActualizados() + resultado.actualizados());
                syncState.setTsUltimaCarga(Instant.now());
                syncStateRepo.save(syncState);

                syncLogRepo.save(SyncLog.builder()
                        .ejecucionId(ejecucionId)
                        .eje(EJE_GLOBAL)
                        .pagina(pag)
                        .registrosNuevos(resultado.nuevas())
                        .registrosActualizados(resultado.actualizados())
                        .errores(resultado.rechazadas())
                        .ts(Instant.now())
                        .build());

                if (pag % 100 == 0) {
                    log.info("BDNS import global: pág. {}/{} — {} nuevos acumulados",
                            pag, maxPaginas + 1, nuevosTotal);
                }

                if (pagina.esUltima()) {
                    log.info("BDNS import global: completado (last=true) — {} páginas, {} nuevos",
                            pag + 1, nuevosTotal);
                    break;
                }

                pag++;
                if (efectiveDelayMs > 0) Thread.sleep(efectiveDelayMs);
            }

            syncState.setEstado(cancelado.get() ? SyncState.Estado.ERROR : SyncState.Estado.COMPLETADO);
            syncState.setTsUltimaCarga(Instant.now());
            syncStateRepo.save(syncState);

        } catch (InterruptedException e) {
            syncState.setEstado(SyncState.Estado.ERROR);
            syncState.setTsUltimaCarga(Instant.now());
            syncStateRepo.save(syncState);
            throw e;
        } catch (Exception e) {
            syncState.setEstado(SyncState.Estado.ERROR);
            syncState.setTsUltimaCarga(Instant.now());
            syncStateRepo.save(syncState);
            log.error("BDNS import global: error en pág. {}: {}", pag, e.getMessage());
        }

        return nuevosTotal;
    }
}