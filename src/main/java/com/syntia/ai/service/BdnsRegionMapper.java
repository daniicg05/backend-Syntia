package com.syntia.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Servicio encargado de gestionar el mapeo y la extracción robusta
 * de la región (Nivel 2 / Comunidad Autónoma) desde los diferentes
 * formatos que devuelve la BDNS, enriqueciendo las convocatorias.
 */
@Slf4j
@Service
public class BdnsRegionMapper {

    private final BdnsClientService bdnsClientService;

    // Caché en memoria para resolver: texto descriptivo -> Info de la región
    private final Map<String, RegionInfo> regionNameToInfoCache = new ConcurrentHashMap<>();
    private final Map<Integer, RegionInfo> regionIdToInfoCache = new ConcurrentHashMap<>();

    // Expresión regular para quitar diacríticos (tildes)
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    // Estructura interna para conocer tipo de región y sus padres
    private record RegionInfo(int id, String nutsCode, Integer parentId) {}

    public BdnsRegionMapper(@Lazy BdnsClientService bdnsClientService) {
        this.bdnsClientService = bdnsClientService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initCache() {
        log.info("Inicializando BdnsRegionMapper: Cargando catálogo de regiones desde BDNS...");
        try {
            List<BdnsClientService.RegionItem> regiones = bdnsClientService.fetchRegiones();
            
            if (regiones != null && !regiones.isEmpty()) {
                int cacheados = 0;
                for (BdnsClientService.RegionItem r : regiones) {
                    if (r.id() != null && r.descripcion() != null) {
                        String rawDesc = r.descripcion().trim();
                        // Extraer posible código NUTS (ej: ES114, ES11, etc.)
                        String nuts = "";
                        String[] parts = rawDesc.split("\\s*-\\s*", 2);
                        if (parts.length == 2 && (parts[0].startsWith("ES") || parts[0].matches("\\d+"))) {
                            nuts = parts[0].toUpperCase();
                        }
                        
                        Integer parentId = r.parentId() != null ? r.parentId().intValue() : null;
                        RegionInfo info = new RegionInfo(r.id().intValue(), nuts, parentId);
                        
                        String normalized = normalize(rawDesc);
                        regionNameToInfoCache.put(normalized, info);
                        regionIdToInfoCache.put(info.id(), info);
                        cacheados++;
                    }
                }
                log.info("BdnsRegionMapper: {} regiones cacheadas exitosamente de {} devueltas.", cacheados, regiones.size());
            } else {
                log.warn("BdnsRegionMapper: La API de regiones devolvió una lista vacía. La caché está vacía.");
            }
        } catch (Exception e) {
            log.error("BdnsRegionMapper: Error al poblar la caché de regiones: {}", e.getMessage());
        }
    }

    /**
     * Normaliza un texto de región a mayúsculas, sin tildes,
     * eliminando sufijos estándares como "ES30 - " y limpiando espacios.
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        
        String clean = text.trim().toUpperCase();
        
        // Limpiamos los prefijos estilo NUTS como 'ES30 - ' o numericos como '10 - '
        clean = clean.replaceFirst("^(ES[A-Z0-9]*|\\d+)\\s*-\\s*", "");
        
        // Quitar las tildes/diacríticos usando Form.NFD y Pattern
        String decomposed = Normalizer.normalize(clean, Normalizer.Form.NFD);
        clean = DIACRITICS_PATTERN.matcher(decomposed).replaceAll("");
        
        return clean;
    }

    /**
     * Punto de entrada del ETL: Extrae la Región ID a través de varios mecanismos en el JSON crudo.
     * Tolerante a múltiples estructuras de la BDNS.
     */
    public Integer extraerRegionId(Map<String, Object> json) {
        if (json == null) return null;

        ensureCacheLoaded();

        Integer n2Id = extraerDesdeRegiones(json, true);
        if (n2Id != null) return n2Id;

        // 2. localizacion.comunidadAutonoma
        Object locObj = json.get("localizacion");
        if (locObj instanceof Map<?,?> locMap) {
            Object ccaaObj = locMap.get("comunidadAutonoma");
            if (ccaaObj instanceof String text) {
                RegionInfo mapped = mapTextoAInfo(text);
                if (mapped != null) return ascendToRegion(mapped);
            }
        }

        // 3. Fallback a texto en nivel2 ("ILLES BALEARS")
        Object nivel2Obj = json.get("nivel2");
        if (nivel2Obj instanceof String text) {
            RegionInfo mapped = mapTextoAInfo(text);
            if (mapped != null) return ascendToRegion(mapped);
        }

        return null;
    }

    /**
     * Extrae la Provincia ID a través de varios mecanismos en el JSON crudo.
     */
    public Integer extraerProvinciaId(Map<String, Object> json) {
        if (json == null) return null;

        ensureCacheLoaded();

        Integer n3Id = extraerDesdeRegiones(json, false);
        if (n3Id != null) return n3Id;

        // 2. localizacion.provincia
        Object locObj = json.get("localizacion");
        if (locObj instanceof Map<?,?> locMap) {
            Object provObj = locMap.get("provincia");
            if (provObj instanceof String text) {
                RegionInfo mapped = mapTextoAInfo(text);
                if (mapped != null && isProvincia(mapped)) return mapped.id();
            }
        }

        // 3. Fallback a texto en nivel3
        Object nivel3Obj = json.get("nivel3");
        if (nivel3Obj instanceof String text) {
            RegionInfo mapped = mapTextoAInfo(text);
            if (mapped != null && isProvincia(mapped)) return mapped.id();
        }

        return null;
    }

    private Integer extraerDesdeRegiones(Map<String, Object> json, boolean asRegion) {
        Object regionesObj = json.get("regiones");
        List<?> regionesList = null;

        if (regionesObj instanceof List<?> rl && !rl.isEmpty()) {
            regionesList = rl;
        } else {
            // Algunas convocatorias podrían venir con provincias en array
            Object provObj = json.get("provincias");
            if (provObj instanceof List<?> pl && !pl.isEmpty()) {
                regionesList = pl;
            }
        }

        if (regionesList == null || regionesList.isEmpty()) {
            return null;
        }

        for (int i = regionesList.size() - 1; i >= 0; i--) {
            Object item = regionesList.get(i);
            RegionInfo info = null;

            if (item instanceof Map<?,?> rm) {
                Object rId = rm.get("id");
                Object rDesc = rm.get("descripcion");
                if (rId instanceof Number n) {
                    info = regionIdToInfoCache.get(n.intValue());
                } else if (rDesc instanceof String s) {
                    info = mapTextoAInfo(s);
                }
            } else if (item instanceof Number n) {
                info = regionIdToInfoCache.get(n.intValue());
            } else if (item instanceof String s) {
                info = mapTextoAInfo(s);
            }

            if (info != null) {
                if (asRegion) {
                    Integer regionId = ascendToRegion(info);
                    if (regionId != null) return regionId;
                } else {
                    if (isProvincia(info)) return info.id();
                }
            }
        }
        return null;
    }

    private RegionInfo mapTextoAInfo(String rawText) {
        String norm = normalize(rawText);
        if (norm.isEmpty()) return null;
        
        RegionInfo info = regionNameToInfoCache.get(norm);
        if (info != null) {
            return info;
        }

        log.debug("BdnsRegionMapper: No se encontró mapeo para '{}' (normalizado: '{}')", rawText, norm);
        return null;
    }
    
    private void ensureCacheLoaded() {
        if (!regionIdToInfoCache.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (regionIdToInfoCache.isEmpty()) {
                initCache();
            }
        }
    }

    private boolean isProvincia(RegionInfo info) {
        // NUTS3: ej ES114 (Empieza por ES y tiene 5 caracteres en total) o similar
        // También podemos asumir que si no es nivel ES o ESX o ESXX, tiene más especificidad.
        if (info.nutsCode().startsWith("ES") && info.nutsCode().length() >= 5) return true;
        // Si no tiene código NUTS o no cumple, como heurística: si su nivel de profundidad
        // es >= 3 asumiendo raiz=0, norOeste=1, comunidad=2, prov=3
        return info.parentId() != null && ascertainLevel(info) >= 3;
    }
    
    private Integer ascendToRegion(RegionInfo info) {
        // Buscamos ascender hasta el nivel NUTS2 (ej: ES11)
        RegionInfo current = info;
        while (current != null) {
            if (current.nutsCode().startsWith("ES") && current.nutsCode().length() == 4) {
                return current.id();
            }
            if (current.parentId() == null) break;
            current = regionIdToInfoCache.get(current.parentId());
        }
        
        // Si no encontramos NUTS2 (por ej. si es una clasificación rara),
        // devolvemos el id original o un heurístico de nivel 2.
        int level = ascertainLevel(info);
        if (level == 2) return info.id();
        
        current = info;
        while (current != null && current.parentId() != null) {
            int curlvl = ascertainLevel(current);
            if (curlvl == 2) return current.id();
            current = regionIdToInfoCache.get(current.parentId());
        }
        
        return info.id();
    }
    
    private int ascertainLevel(RegionInfo info) {
        int level = 0;
        RegionInfo curr = info;
        while (curr != null && curr.parentId() != null) {
            level++;
            curr = regionIdToInfoCache.get(curr.parentId());
        }
        return level;
    }
}
