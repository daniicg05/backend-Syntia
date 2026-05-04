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
import java.util.List;
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

    @Value("${bdns.import.limite-convocatorias:5000}")
    private int limiteConvocatoriasDefault;

    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaService convocatoriaService;
    private final SyncStateRepository syncStateRepo;
    private final SyncLogRepository syncLogRepo;
    private final RegionService regionService;

    public BdnsImportEstrategiaService(BdnsClientService bdnsClientService,
                                       ConvocatoriaService convocatoriaService,
                                       SyncStateRepository syncStateRepo,
                                       SyncLogRepository syncLogRepo,
                                       RegionService regionService) {
        this.bdnsClientService = bdnsClientService;
        this.convocatoriaService = convocatoriaService;
        this.syncStateRepo = syncStateRepo;
        this.syncLogRepo = syncLogRepo;
        this.regionService = regionService;
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
                            long delayMsOverride,
                            Integer limiteConvocatorias) throws InterruptedException {
        long efectiveDelayMs = delayMsOverride >= 0 ? delayMsOverride : this.delayMs;
        int limite = Math.max(1, limiteConvocatorias != null ? limiteConvocatorias : limiteConvocatoriasDefault);
        String ejecucionId = UUID.randomUUID().toString();
        log.info("BDNS import global: ejecucionId={} modo={} delayMs={} limite={}", ejecucionId, modo, efectiveDelayMs, limite);

        // Sincronizar catálogo de regiones si la tabla está vacía
        if (regionService.count() == 0) {
            log.info("BDNS import: tabla regiones vacía — sincronizando catálogo...");
            try {
                int totalRegiones = regionService.sincronizarRegiones();
                log.info("BDNS import: catálogo de regiones cargado ({} nodos)", totalRegiones);
            } catch (Exception e) {
                log.warn("BDNS import: no se pudo sincronizar el catálogo de regiones: {}", e.getMessage());
                // No abortar el ETL si falla la carga de regiones
            }
        }

        SyncState syncState = syncStateRepo.findByEje(EJE_GLOBAL)
                .orElse(SyncState.builder().eje(EJE_GLOBAL).build());

        int paginaInicio = 0;
        boolean esReanudacion = modo == ModoImportacion.INCREMENTAL && syncState.getUltimaPaginaOk() >= 0;
        if (esReanudacion) {
            paginaInicio = syncState.getUltimaPaginaOk() + 1;
            log.info("BDNS import global: reanudando desde pág. {} (última ok: {})",
                    paginaInicio, syncState.getUltimaPaginaOk());
        }

        Long ultimaConvocatoriaLocal = modo == ModoImportacion.NUEVAS
                ? convocatoriaService.obtenerMaxNumeroConvocatoriaNumerico()
                : null;
        boolean actualizaEstadoGlobal = modo != ModoImportacion.NUEVAS;

        if (actualizaEstadoGlobal) {
            syncState.setEstado(SyncState.Estado.EN_PROGRESO);
            syncState.setTsInicio(Instant.now());
            syncState.setTsUltimaCarga(null);
            if (modo == ModoImportacion.FULL || paginaInicio == 0) {
                syncState.setUltimaPaginaOk(-1);
                syncState.setRegistrosNuevos(0);
                syncState.setRegistrosActualizados(0);
            }
            syncState = syncStateRepo.save(syncState);
        }

        int pag = paginaInicio;
        int nuevosTotal = 0;
        int procesadas = 0;
        int maxPaginas = Integer.MAX_VALUE;
        boolean limiteAlcanzado = false;
        boolean encontroUltimaLocal = false;

        try {
            while (pag <= maxPaginas && !cancelado.get() && procesadas < limite && !encontroUltimaLocal) {
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

                List<com.syntia.ai.model.dto.ConvocatoriaDTO> candidatas = new java.util.ArrayList<>();
                for (com.syntia.ai.model.dto.ConvocatoriaDTO dto : pagina.items()) {
                    if (procesadas >= limite) {
                        limiteAlcanzado = true;
                        break;
                    }
                    Long numero = parseNumeroConvocatoria(dto.getNumeroConvocatoria());
                    if (modo == ModoImportacion.NUEVAS && ultimaConvocatoriaLocal != null
                            && numero != null && numero <= ultimaConvocatoriaLocal) {
                        encontroUltimaLocal = true;
                        break;
                    }
                    candidatas.add(dto);
                    procesadas++;
                }

                // Enriquecer cada convocatoria con datos del endpoint de detalle
                for (com.syntia.ai.model.dto.ConvocatoriaDTO dto : candidatas) {
                    bdnsClientService.enriquecerConDetalle(dto);
                }

                ResultadoPersistencia resultado = convocatoriaService.persistirNuevas(candidatas);
                nuevosTotal += resultado.nuevas();

                if (actualizaEstadoGlobal && candidatas.size() == pagina.items().size()) {
                    syncState.setUltimaPaginaOk(pag);
                    syncState.setRegistrosNuevos(syncState.getRegistrosNuevos() + resultado.nuevas());
                    syncState.setRegistrosActualizados(syncState.getRegistrosActualizados() + resultado.actualizados());
                    syncState.setTsUltimaCarga(Instant.now());
                    syncStateRepo.save(syncState);
                }

                syncLogRepo.save(SyncLog.builder()
                        .ejecucionId(ejecucionId)
                        .eje(modo == ModoImportacion.NUEVAS ? "GLOBAL_NUEVAS" : EJE_GLOBAL)
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

            if (actualizaEstadoGlobal) {
            boolean pendiente = (limiteAlcanzado || procesadas >= limite) && pag <= maxPaginas && !cancelado.get();
            syncState.setEstado(cancelado.get() || pendiente ? SyncState.Estado.ERROR : SyncState.Estado.COMPLETADO);
            syncState.setTsUltimaCarga(Instant.now());
            syncStateRepo.save(syncState);
            }

        } catch (InterruptedException e) {
            if (actualizaEstadoGlobal) {
            syncState.setEstado(SyncState.Estado.ERROR);
            syncState.setTsUltimaCarga(Instant.now());
            syncStateRepo.save(syncState);
            }
            throw e;
        } catch (Exception e) {
            if (actualizaEstadoGlobal) {
            syncState.setEstado(SyncState.Estado.ERROR);
            syncState.setTsUltimaCarga(Instant.now());
            syncStateRepo.save(syncState);
            }
            log.error("BDNS import global: error en pág. {}: {}", pag, e.getMessage());
        }

        return nuevosTotal;
    }

    private Long parseNumeroConvocatoria(String numeroConvocatoria) {
        if (numeroConvocatoria == null || numeroConvocatoria.isBlank()) return null;
        try {
            return Long.parseLong(numeroConvocatoria.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
