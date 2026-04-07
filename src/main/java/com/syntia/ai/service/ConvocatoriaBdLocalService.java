package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de búsqueda de convocatorias en la base de datos local.
 * Usado en modo gratuito como alternativa a la API live de BDNS.
 */
@Slf4j
@Service
public class ConvocatoriaBdLocalService {

    private final ConvocatoriaRepository convocatoriaRepository;
    private final ConvocatoriaService convocatoriaService;

    public ConvocatoriaBdLocalService(ConvocatoriaRepository convocatoriaRepository,
                                      ConvocatoriaService convocatoriaService) {
        this.convocatoriaRepository = convocatoriaRepository;
        this.convocatoriaService = convocatoriaService;
    }

    /**
     * Busca convocatorias en la BD local usando los filtros del proyecto y perfil.
     * Devuelve el mismo formato ConvocatoriaDTO que la API live de BDNS.
     */
    public List<ConvocatoriaDTO> buscar(Proyecto proyecto, Perfil perfil) {
        String keyword = resolverKeyword(proyecto, perfil);
        String ubicacion = resolverUbicacion(proyecto, perfil);

        log.info("Búsqueda local (modo gratuito): keyword='{}' ubicacion='{}'", keyword, ubicacion);

        List<Convocatoria> resultados = convocatoriaRepository.buscarParaModoGratuito(keyword, ubicacion);

        log.info("Búsqueda local: {} convocatorias encontradas en BD", resultados.size());

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