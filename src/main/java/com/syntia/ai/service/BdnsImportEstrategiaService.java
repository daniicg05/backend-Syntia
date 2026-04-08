package com.syntia.ai.service;

import com.syntia.ai.model.SyncLog;
import com.syntia.ai.model.SyncState;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
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
     * Ejecuta la importación completa por todos los ejes territoriales.
     *
     * @param onProgreso callback invocado tras cada página: (ejeActual, totalNuevosAcumulados)
     * @return total de registros nuevos importados
     */
    public int importarTodo(BiConsumer<String, Integer> onProgreso) throws InterruptedException {
        String ejecucionId = UUID.randomUUID().toString();
        log.info("BDNS estrategia: ejecucionId={}", ejecucionId);

        int total = 0;

        total += importarEje("ESTADO", null, total, onProgreso, ejecucionId);
        for (String ccaa : CCAA) {
            total += importarEje("AUTONOMICA", ccaa, total, onProgreso, ejecucionId);
        }
        total += importarEje("LOCAL", null, total, onProgreso, ejecucionId);
        total += importarEje("OTROS", null, total, onProgreso, ejecucionId);

        return total;
    }

    private int importarEje(String nivel1, String nivel2, int totalPrevio,
                             BiConsumer<String, Integer> onProgreso,
                             String ejecucionId) throws InterruptedException {
        String ejeKey = nivel2 != null ? nivel1 + " – " + nivel2 : nivel1;
        log.info("BDNS estrategia: iniciando eje [{}]", ejeKey);

        // Crear o resetear SyncState para este eje
        SyncState syncState = syncStateRepo.findByEje(ejeKey)
                .orElse(SyncState.builder().eje(ejeKey).build());
        syncState.setEstado(SyncState.Estado.EN_PROGRESO);
        syncState.setTsInicio(Instant.now());
        syncState.setTsUltimaCarga(null);
        syncState.setUltimaPaginaOk(-1);
        syncState.setRegistrosNuevos(0);
        syncState.setRegistrosActualizados(0);
        syncState = syncStateRepo.save(syncState);

        int pag = 0;
        int nuevosEje = 0;

        try {
            while (true) {
                onProgreso.accept(ejeKey + " – pág. " + pag, totalPrevio + nuevosEje);

                List<ConvocatoriaDTO> pagina = bdnsClientService.importarPorEje(nivel1, nivel2, pag, TAM_PAGINA);

                if (pagina.isEmpty()) {
                    log.info("BDNS estrategia: eje [{}] completado — {} páginas, {} nuevos", ejeKey, pag, nuevosEje);
                    break;
                }

                int nuevasPag = convocatoriaService.persistirNuevas(pagina);
                nuevosEje += nuevasPag;

                // Persistir progreso por página
                syncState.setUltimaPaginaOk(pag);
                syncState.setRegistrosNuevos(syncState.getRegistrosNuevos() + nuevasPag);
                syncState.setTsUltimaCarga(Instant.now());
                syncStateRepo.save(syncState);

                syncLogRepo.save(SyncLog.builder()
                        .ejecucionId(ejecucionId)
                        .eje(ejeKey)
                        .pagina(pag)
                        .registrosNuevos(nuevasPag)
                        .registrosActualizados(0)
                        .errores(0)
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