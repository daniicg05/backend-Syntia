package com.syntia.ai.service;

import com.syntia.ai.model.SyncLog;
import com.syntia.ai.model.SyncState;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.model.dto.ResultadoPersistencia;
import com.syntia.ai.repository.SyncLogRepository;
import com.syntia.ai.repository.SyncStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Estrategia de importación masiva BDNS por ejes territoriales.
 * <p>
 * En lugar de paginar linealmente (que devuelve un subconjunto rotativo),
 * itera por cada combinación de nivel1 + nivel2:
 * <ul>
 *   <li>ESTADO (todas las páginas)</li>
 *   <li>AUTONOMICA × 19 CCAA (todas las páginas de cada una)</li>
 *   <li>LOCAL (todas las páginas)</li>
 *   <li>OTROS (todas las páginas)</li>
 * </ul>
 * La deduplicación por idBdns evita duplicados entre ejes.
 */
@Slf4j
@Service
public class BdnsImportEstrategiaService {

    private static final int TAM_PAGINA = 50;

    /** Las 19 CCAA exactas que acepta nivel2 en la API BDNS. */
    private static final List<String> CCAA = List.of(
            "Andalucía", "Aragón", "Asturias", "Baleares", "Canarias",
            "Cantabria", "Castilla y León", "Castilla-La Mancha", "Cataluña",
            "Comunidad Valenciana", "Extremadura", "Galicia", "Madrid",
            "Murcia", "Navarra", "País Vasco", "La Rioja", "Ceuta", "Melilla"
    );

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
     * Ejecuta la importación por todos los ejes territoriales.
     *
     * @param onProgreso callback invocado tras cada página: (ejeActual, totalNuevosAcumulados)
     * @param modo       FULL reinicia todos los ejes; INCREMENTAL salta los COMPLETADOS y reanuda los ERROR
     * @return total de registros nuevos importados
     */
    public int importarTodo(BiConsumer<String, Integer> onProgreso,
                            ModoImportacion modo) throws InterruptedException {
        String ejecucionId = UUID.randomUUID().toString();
        log.info("BDNS estrategia: ejecucionId={} modo={}", ejecucionId, modo);

        int total = 0;

        total += importarEje("ESTADO", null, total, onProgreso, ejecucionId, modo);
        for (String ccaa : CCAA) {
            total += importarEje("AUTONOMICA", ccaa, total, onProgreso, ejecucionId, modo);
        }
        total += importarEje("LOCAL", null, total, onProgreso, ejecucionId, modo);
        total += importarEje("OTROS", null, total, onProgreso, ejecucionId, modo);

        return total;
    }

    private int importarEje(String nivel1, String nivel2, int totalPrevio,
                             BiConsumer<String, Integer> onProgreso,
                             String ejecucionId,
                             ModoImportacion modo) throws InterruptedException {
        String ejeKey = nivel2 != null ? nivel1 + " – " + nivel2 : nivel1;

        SyncState syncState = syncStateRepo.findByEje(ejeKey)
                .orElse(SyncState.builder().eje(ejeKey).build());

        // ── Lógica de modo incremental ────────────────────────────────────────
        if (modo == ModoImportacion.INCREMENTAL && syncState.getEstado() == SyncState.Estado.COMPLETADO) {
            log.info("BDNS estrategia: eje [{}] ya COMPLETADO — omitido en modo INCREMENTAL", ejeKey);
            onProgreso.accept(ejeKey + " [omitido]", totalPrevio);
            return 0;
        }

        // En INCREMENTAL, reanudar desde la página siguiente a la última ok
        int paginaInicio = 0;
        if (modo == ModoImportacion.INCREMENTAL && syncState.getEstado() == SyncState.Estado.ERROR
                && syncState.getUltimaPaginaOk() >= 0) {
            paginaInicio = syncState.getUltimaPaginaOk() + 1;
            log.info("BDNS estrategia: eje [{}] reanudado desde pág. {} (última ok: {})",
                    ejeKey, paginaInicio, syncState.getUltimaPaginaOk());
        } else {
            log.info("BDNS estrategia: iniciando eje [{}] desde pág. 0", ejeKey);
        }

        // Inicializar / resetear estado para esta ejecución
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
        int nuevosEje = 0;

        try {
            while (true) {
                onProgreso.accept(ejeKey + " – pág. " + pag, totalPrevio + nuevosEje);

                List<ConvocatoriaDTO> pagina = bdnsClientService.importarPorEje(nivel1, nivel2, pag, TAM_PAGINA);

                if (pagina.isEmpty()) {
                    log.info("BDNS estrategia: eje [{}] completado — {} páginas, {} nuevos", ejeKey, pag, nuevosEje);
                    break;
                }

                ResultadoPersistencia resultado = convocatoriaService.persistirNuevas(pagina);
                nuevosEje += resultado.nuevas();

                syncState.setUltimaPaginaOk(pag);
                syncState.setRegistrosNuevos(syncState.getRegistrosNuevos() + resultado.nuevas());
                syncState.setTsUltimaCarga(Instant.now());
                syncStateRepo.save(syncState);

                syncLogRepo.save(SyncLog.builder()
                        .ejecucionId(ejecucionId)
                        .eje(ejeKey)
                        .pagina(pag)
                        .registrosNuevos(resultado.nuevas())
                        .registrosActualizados(0)
                        .errores(resultado.rechazadas())
                        .ts(Instant.now())
                        .build());

                if (pag % 10 == 0) {
                    log.info("BDNS estrategia: eje [{}] pág. {} — {} nuevos en este eje", ejeKey, pag, nuevosEje);
                }

                if (pagina.size() < TAM_PAGINA) {
                    log.info("BDNS estrategia: eje [{}] completado — {} páginas, {} nuevos", ejeKey, pag + 1, nuevosEje);
                    break;
                }

                pag++;
                Thread.sleep(delayMs);
            }

            syncState.setEstado(SyncState.Estado.COMPLETADO);
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
            log.error("BDNS estrategia: error en eje [{}] pág. {}: {}", ejeKey, pag, e.getMessage());
        }

        return nuevosEje;
    }
}