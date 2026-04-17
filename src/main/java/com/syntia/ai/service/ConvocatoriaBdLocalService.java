package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

    /**
     * Busca convocatorias en la BD local usando los filtros del proyecto y perfil.
     * Devuelve el mismo formato ConvocatoriaDTO que la API live de BDNS.
     */
    public List<ConvocatoriaDTO> buscar(Proyecto proyecto, Perfil perfil) {
        String keyword = resolverKeyword(proyecto, perfil);
        String ubicacionRaw = resolverUbicacion(proyecto, perfil);
        
        Integer regionIdMapped = UbicacionNormalizador.normalizarARegionId(ubicacionRaw);
        boolean filtrarRegion = regionIdMapped != null;
        Set<Integer> regionIds = filtrarRegion ? regionService.obtenerDescendientesIds(regionIdMapped.longValue()) : Set.of();

        log.info("Bsqueda local (modo gratuito): keyword='{}' ubicacionRaw='{}' regionId={}, filtrarRegion={}", 
                keyword, ubicacionRaw, regionIdMapped, filtrarRegion);

        List<Convocatoria> resultados = convocatoriaRepository.buscarParaModoGratuitoConRegion(
                keyword, 
                filtrarRegion, 
                regionIds, 
                ubicacionRaw != null ? ubicacionRaw : ""
        );

        log.info("Bsqueda local: {} convocatorias encontradas en BD", resultados.size());

        return resultados.stream()
                .map(convocatoriaService::toDTO)
                .toList();
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