package com.syntia.ai.service;

import com.syntia.ai.model.BdnsFinalidad;
import com.syntia.ai.model.BdnsInstrumento;
import com.syntia.ai.model.BdnsOrgano;
import com.syntia.ai.model.BdnsRegion;
import com.syntia.ai.repository.BdnsFinalidadesRepository;
import com.syntia.ai.repository.BdnsInstrumentosRepository;
import com.syntia.ai.repository.BdnsOrganosRepository;
import com.syntia.ai.repository.BdnsRegionesRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CatalogosBdnsService {

    private static final String BASE = "https://www.infosubvenciones.es/bdnstrans/api";

    private final BdnsClientService bdnsHttpClient;
    private final BdnsRegionesRepository regionesRepo;
    private final BdnsFinalidadesRepository finalidadesRepo;
    private final BdnsInstrumentosRepository instrumentosRepo;
    private final BdnsOrganosRepository organosRepo;

    @PostConstruct
    @Scheduled(fixedRate = 604800000L)
    public void sincronizarTodos() {
        log.info("[BDNS-CAT] Iniciando sincronizacion de catalogos...");
        sincronizarRegiones();
        sincronizarFinalidades();
        sincronizarInstrumentos();
        sincronizarOrganos();
        log.info("[BDNS-CAT] Sincronizacion completada.");
    }

    private void sincronizarRegiones() {
        try {
            List<Map<String, Object>> data = bdnsHttpClient.getRawList(BASE + "/regiones");
            regionesRepo.deleteAll();
            regionesRepo.saveAll(data.stream().map(m -> {
                BdnsRegion r = new BdnsRegion();
                r.setId(((Number) m.get("id")).intValue());
                r.setNombre((String) m.get("nombre"));
                r.setNivel((String) m.get("nivel"));
                r.setActivo(true);
                r.setSyncAt(LocalDateTime.now());
                return r;
            }).toList());
            log.info("[BDNS-CAT] Regiones sincronizadas: {}", regionesRepo.count());
        } catch (Exception e) {
            log.warn("[BDNS-CAT] Error sync regiones: {}", e.getMessage());
        }
    }

    private void sincronizarFinalidades() {
        try {
            List<Map<String, Object>> data = bdnsHttpClient.getRawList(BASE + "/finalidades?vpd=GE");
            finalidadesRepo.deleteAll();
            finalidadesRepo.saveAll(data.stream().map(m -> {
                BdnsFinalidad f = new BdnsFinalidad();
                f.setId(((Number) m.get("id")).intValue());
                f.setNombre((String) m.get("nombre"));
                f.setActivo(true);
                f.setSyncAt(LocalDateTime.now());
                return f;
            }).toList());
            log.info("[BDNS-CAT] Finalidades sincronizadas: {}", finalidadesRepo.count());
        } catch (Exception e) {
            log.warn("[BDNS-CAT] Error sync finalidades: {}", e.getMessage());
        }
    }

    private void sincronizarInstrumentos() {
        try {
            List<Map<String, Object>> data = bdnsHttpClient.getRawList(BASE + "/instrumentos");
            instrumentosRepo.deleteAll();
            instrumentosRepo.saveAll(data.stream().map(m -> {
                BdnsInstrumento i = new BdnsInstrumento();
                i.setId(((Number) m.get("id")).intValue());
                i.setNombre((String) m.get("nombre"));
                i.setActivo(true);
                i.setSyncAt(LocalDateTime.now());
                return i;
            }).toList());
            log.info("[BDNS-CAT] Instrumentos sincronizados: {}", instrumentosRepo.count());
        } catch (Exception e) {
            log.warn("[BDNS-CAT] Error sync instrumentos: {}", e.getMessage());
        }
    }

    private void sincronizarOrganos() {
        organosRepo.deleteAll();
        for (String tipo : List.of("C", "A", "L", "O")) {
            try {
                List<Map<String, Object>> data = bdnsHttpClient.getRawList(
                        BASE + "/organos?vpd=GE&idAdmon=" + tipo);
                organosRepo.saveAll(data.stream().map(m -> {
                    BdnsOrgano o = new BdnsOrgano();
                    o.setId(((Number) m.get("id")).intValue());
                    o.setNombre((String) m.get("nombre"));
                    o.setTipoAdmon(tipo);
                    o.setActivo(true);
                    o.setSyncAt(LocalDateTime.now());
                    return o;
                }).toList());
                log.info("[BDNS-CAT] Organos sincronizados tipo {}: {}", tipo, data.size());
            } catch (Exception e) {
                log.warn("[BDNS-CAT] Error sync organos tipo={}: {}", tipo, e.getMessage());
            }
        }
        log.info("[BDNS-CAT] Organos sincronizados total: {}", organosRepo.count());
    }

    @Cacheable(value = "bdns-catalogos", key = "'region:' + #ubicacion")
    public List<Integer> resolverRegionIds(String ubicacion) {
        if (ubicacion == null || ubicacion.isBlank()) {
            return List.of();
        }
        return regionesRepo.findByNombreContainingIgnoreCase(ubicacion)
                .stream()
                .map(BdnsRegion::getId)
                .toList();
    }

    @Cacheable(value = "bdns-catalogos", key = "'finalidad:' + #sector")
    public Integer resolverFinalidadId(String sector) {
        if (sector == null || sector.isBlank()) {
            return null;
        }
        List<BdnsFinalidad> r = finalidadesRepo.findBestMatch(sector);
        return r.isEmpty() ? null : r.get(0).getId();
    }

    @Cacheable(value = "bdns-catalogos", key = "'instrumento:' + #nombre")
    public List<Integer> resolverInstrumentoIds(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return List.of();
        }
        return instrumentosRepo.findByNombreContainingIgnoreCase(nombre)
                .stream()
                .map(BdnsInstrumento::getId)
                .toList();
    }

    public List<BdnsRegion> getAllRegiones() {
        return regionesRepo.findAll();
    }

    public List<BdnsFinalidad> getAllFinalidades() {
        return finalidadesRepo.findAll();
    }

    public List<BdnsInstrumento> getAllInstrumentos() {
        return instrumentosRepo.findAll();
    }

    public List<BdnsOrgano> getOrganos(String tipo) {
        return tipo != null ? organosRepo.findByTipoAdmon(tipo) : organosRepo.findAll();
    }
}

