package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servicio de bsqueda de convocatorias en la base de datos local.
 * Usado en modo gratuito como alternativa a la API live de BDNS.
 */
@Slf4j
@Service
public class ConvocatoriaBdLocalService {

    private final ConvocatoriaRepository convocatoriaRepository;
    private final ConvocatoriaService convocatoriaService;
    private final RegionService regionService;

    public ConvocatoriaBdLocalService(ConvocatoriaRepository convocatoriaRepository,
                                      ConvocatoriaService convocatoriaService,
                                      RegionService regionService) {
        this.convocatoriaRepository = convocatoriaRepository;
        this.convocatoriaService = convocatoriaService;
        this.regionService = regionService;
    }

    /** Palabras vacías que no aportan valor a la búsqueda. */
    private static final Set<String> STOP_WORDS = Set.of(
            "e", "y", "o", "de", "del", "la", "el", "los", "las", "un", "una",
            "en", "con", "por", "para", "al", "a", "su", "sus", "se", "es");

    /**
     * Busca convocatorias en la BD local usando los filtros del proyecto y perfil.
     * Tokeniza el keyword en palabras individuales para mejorar el recall cuando
     * el sector del proyecto no coincide literalmente con los sectores CNAE de la BD.
     */
    public List<ConvocatoriaDTO> buscar(Proyecto proyecto, Perfil perfil) {
        String keyword = resolverKeyword(proyecto, perfil);
        String ubicacionRaw = resolverUbicacion(proyecto, perfil);

        Integer regionIdMapped = UbicacionNormalizador.normalizarARegionId(ubicacionRaw);
        boolean filtrarRegion = regionIdMapped != null;
        Set<Integer> regionIds = filtrarRegion ? regionService.obtenerDescendientesIds(regionIdMapped.longValue()) : Set.of(-1);
        String ubicacionTexto = ubicacionRaw != null ? ubicacionRaw : "";

        // Preparar parámetros LIKE ya en minúsculas con comodines
        // (evita el bug lower(bytea) de Hibernate 6 + PostgreSQL)
        String ubicacionLike = ubicacionTexto.isBlank() ? null : "%" + ubicacionTexto.toLowerCase() + "%";

        log.info("Búsqueda local (modo gratuito): keyword='{}' ubicacionRaw='{}' regionId={}, filtrarRegion={}",
                keyword, ubicacionRaw, regionIdMapped, filtrarRegion);

        // 1. Búsqueda con el keyword completo
        String kwLike = wrapLike(keyword);
        List<Convocatoria> resultados = convocatoriaRepository.buscarParaModoGratuitoConRegion(
                kwLike, filtrarRegion, regionIds, ubicacionLike != null ? ubicacionLike : "");

        // 2. Si no hay resultados, buscar por cada palabra significativa del keyword
        if (resultados.isEmpty() && keyword != null && keyword.contains(" ")) {
            List<String> tokens = tokenizar(keyword);
            log.info("Búsqueda local: 0 resultados con keyword completo, reintentando con tokens: {}", tokens);

            Map<Long, Convocatoria> merged = new LinkedHashMap<>();
            for (String token : tokens) {
                List<Convocatoria> parcial = convocatoriaRepository.buscarParaModoGratuitoConRegion(
                        wrapLike(token), filtrarRegion, regionIds, ubicacionLike != null ? ubicacionLike : "");
                for (Convocatoria c : parcial) {
                    merged.putIfAbsent(c.getId(), c);
                }
            }
            resultados = new ArrayList<>(merged.values());
        }

        // 3. Si sigue sin resultados y también tenemos nombre de proyecto, intentar con él
        if (resultados.isEmpty() && keyword != null && proyecto.getNombre() != null
                && !proyecto.getNombre().equalsIgnoreCase(keyword)) {
            List<String> tokensNombre = tokenizar(proyecto.getNombre());
            log.info("Búsqueda local: 0 resultados con sector, reintentando con nombre del proyecto: {}", tokensNombre);

            Map<Long, Convocatoria> merged = new LinkedHashMap<>();
            for (String token : tokensNombre) {
                List<Convocatoria> parcial = convocatoriaRepository.buscarParaModoGratuitoConRegion(
                        wrapLike(token), filtrarRegion, regionIds, ubicacionLike != null ? ubicacionLike : "");
                for (Convocatoria c : parcial) {
                    merged.putIfAbsent(c.getId(), c);
                }
            }
            resultados = new ArrayList<>(merged.values());
        }

        // 4. Último recurso: sin filtro de keyword, solo por región (convocatorias recientes)
        if (resultados.isEmpty()) {
            log.info("Búsqueda local: sin coincidencias por keyword, devolviendo convocatorias recientes de la zona");
            resultados = convocatoriaRepository.buscarParaModoGratuitoConRegion(
                    null, filtrarRegion, regionIds, ubicacionLike != null ? ubicacionLike : "");
        }

        log.info("Búsqueda local: {} convocatorias encontradas en BD", resultados.size());

        return resultados.stream()
                .map(convocatoriaService::toDTO)
                .toList();
    }

    /** Envuelve un keyword en comodines LIKE y lo pone en minúsculas, o null si es nulo. */
    private String wrapLike(String kw) {
        return kw == null ? null : "%" + kw.toLowerCase() + "%";
    }

    /**
     * Tokeniza un keyword eliminando stop words y tokens cortos (<3 chars).
     */
    private List<String> tokenizar(String keyword) {
        String[] partes = keyword.toLowerCase().split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : partes) {
            String limpio = p.replaceAll("[^a-záéíóúñü]", "");
            if (limpio.length() >= 3 && !STOP_WORDS.contains(limpio)) {
                tokens.add(limpio);
            }
        }
        return tokens;
    }

    private String resolverKeyword(Proyecto proyecto, Perfil perfil) {
        if (proyecto.getSector() != null && !proyecto.getSector().isBlank()) {
            return proyecto.getSector().trim();
        }
        if (perfil != null && perfil.getSector() != null && !perfil.getSector().isBlank()) {
            return perfil.getSector().trim();
        }
        if (proyecto.getNombre() != null && !proyecto.getNombre().isBlank()) {
            return proyecto.getNombre().trim();
        }
        return null;
    }

    private String resolverUbicacion(Proyecto proyecto, Perfil perfil) {
        if (proyecto.getUbicacion() != null && !proyecto.getUbicacion().isBlank()) {
            return proyecto.getUbicacion().trim();
        }
        if (perfil != null && perfil.getUbicacion() != null && !perfil.getUbicacion().isBlank()) {
            return perfil.getUbicacion().trim();
        }
        return null;
    }
}