package com.syntia.ai.service;

import com.syntia.ai.model.SyncLog;
import com.syntia.ai.model.SyncState;
import com.syntia.ai.model.dto.CoberturaDTO;
import com.syntia.ai.model.dto.ResumenEjecucionDTO;
import com.syntia.ai.model.dto.SyncStateDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.SyncLogRepository;
import com.syntia.ai.repository.SyncStateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de consulta para el panel ETL de administración BDNS.
 * Expone el estado por eje y el historial de ejecuciones sin lógica de importación.
 */
@Service
public class BdnsEtlPanelService {

    private final SyncStateRepository syncStateRepo;
    private final SyncLogRepository syncLogRepo;
    private final ConvocatoriaRepository convocatoriaRepo;

    public BdnsEtlPanelService(SyncStateRepository syncStateRepo,
                                SyncLogRepository syncLogRepo,
                                ConvocatoriaRepository convocatoriaRepo) {
        this.syncStateRepo = syncStateRepo;
        this.syncLogRepo = syncLogRepo;
        this.convocatoriaRepo = convocatoriaRepo;
    }

    /**
     * Devuelve el estado actual de los 23 ejes territoriales ordenados alfabéticamente.
     * Si un eje nunca se ha procesado no aparece en la lista.
     */
    public List<SyncStateDTO> obtenerEstadoEjes() {
        return syncStateRepo.findAllByOrderByEjeAsc().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Devuelve el historial de ejecuciones resumido, ordenado de más reciente a más antiguo.
     * Cada entrada agrupa todos los SyncLog de una misma ejecucionId.
     */
    public List<ResumenEjecucionDTO> obtenerHistorial() {
        List<SyncLog> todos = syncLogRepo.findAllByOrderByTsDesc();

        // Agrupar por ejecucionId manteniendo orden de primera aparición (más reciente primero)
        Map<String, List<SyncLog>> porEjecucion = new LinkedHashMap<>();
        for (SyncLog log : todos) {
            porEjecucion.computeIfAbsent(log.getEjecucionId(), k -> new ArrayList<>()).add(log);
        }

        return porEjecucion.entrySet().stream()
                .map(entry -> resumir(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Devuelve el detalle página a página de una ejecución concreta.
     */
    public List<SyncLog> obtenerLogsEjecucion(String ejecucionId) {
        return syncLogRepo.findByEjecucionIdOrderByTsAsc(ejecucionId);
    }

    /**
     * Calcula el porcentaje de convocatorias en BD que tienen cada campo relleno.
     */
    public CoberturaDTO obtenerCobertura() {
        long total = convocatoriaRepo.count();
        if (total == 0) {
            return new CoberturaDTO(0, List.of());
        }
        List<CoberturaDTO.CampoCobertura> campos = List.of(
                campo("organismo",        convocatoriaRepo.countByOrganismoIsNotNull(),        total),
                campo("fechaPublicacion", convocatoriaRepo.countByFechaPublicacionIsNotNull(), total),
                campo("descripcion",      convocatoriaRepo.countByDescripcionIsNotNull(),      total),
                campo("textoCompleto",    convocatoriaRepo.countByTextoCompletoIsNotNull(),    total),
                campo("sector",           convocatoriaRepo.countBySectorIsNotNull(),           total),
                campo("fechaCierre",      convocatoriaRepo.countByFechaCierreIsNotNull(),      total),
                campo("ubicacion",        convocatoriaRepo.countByUbicacionIsNotNull(),        total)
        );
        return new CoberturaDTO(total, campos);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CoberturaDTO.CampoCobertura campo(String nombre, long conValor, long total) {
        double pct = total > 0 ? Math.round(conValor * 1000.0 / total) / 10.0 : 0.0;
        return new CoberturaDTO.CampoCobertura(nombre, conValor, pct);
    }

    private SyncStateDTO toDTO(SyncState s) {
        return new SyncStateDTO(
                s.getEje(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getUltimaPaginaOk(),
                s.getRegistrosNuevos(),
                s.getRegistrosActualizados(),
                s.getTsInicio(),
                s.getTsUltimaCarga()
        );
    }

    private ResumenEjecucionDTO resumir(String ejecucionId, List<SyncLog> logs) {
        Instant tsInicio = logs.stream().map(SyncLog::getTs).min(Instant::compareTo).orElse(null);
        Instant tsFin    = logs.stream().map(SyncLog::getTs).max(Instant::compareTo).orElse(null);
        long nuevos      = logs.stream().mapToLong(SyncLog::getRegistrosNuevos).sum();
        long actualizados = logs.stream().mapToLong(SyncLog::getRegistrosActualizados).sum();
        long errores     = logs.stream().mapToLong(SyncLog::getErrores).sum();
        long ejes        = logs.stream().map(SyncLog::getEje).distinct().count();
        long paginas     = logs.size();
        return new ResumenEjecucionDTO(ejecucionId, tsInicio, tsFin,
                nuevos, actualizados, errores, ejes, paginas);
    }
}