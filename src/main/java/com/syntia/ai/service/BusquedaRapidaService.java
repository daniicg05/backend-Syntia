package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Plan;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.model.dto.FiltrosBdns;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Slf4j
@Service
public class BusquedaRapidaService {

    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaBdLocalService convocatoriaBdLocalService;
    private final ConvocatoriaRepository convocatoriaRepository;
    private final RecomendacionRepository recomendacionRepository;

    /** Máximo de candidatas a guardar por búsqueda rápida. */
    private static final int MAX_CANDIDATAS = 150;

    public BusquedaRapidaService(BdnsClientService bdnsClientService,
                                 ConvocatoriaBdLocalService convocatoriaBdLocalService,
                                 ConvocatoriaRepository convocatoriaRepository,
                                 RecomendacionRepository recomendacionRepository) {
        this.bdnsClientService = bdnsClientService;
        this.convocatoriaBdLocalService = convocatoriaBdLocalService;
        this.convocatoriaRepository = convocatoriaRepository;
        this.recomendacionRepository = recomendacionRepository;
    }


    @Transactional
    public int buscarYGuardarCandidatas(Proyecto proyecto) {
        // 1. Construir filtros basados ÚNICAMENTE en el proyecto
        FiltrosBdns filtros = BdnsFiltrosBuilder.construir(proyecto, null);
        log.info("Búsqueda rápida: proyecto={} descripcion='{}' regionId={} finalidadId={}",
                proyecto.getId(), filtros.descripcion(), filtros.regionId(), filtros.finalidadId());

        // 2. Buscar según plan del usuario: BD local (gratuito) o API live (premium)
        Plan plan = proyecto.getUsuario().getPlan();
        List<ConvocatoriaDTO> candidatasBdns;
        if (Plan.GRATUITO.equals(plan)) {
            log.info("Búsqueda rápida: modo GRATUITO — usando BD local");
            candidatasBdns = convocatoriaBdLocalService.buscar(proyecto, null);
        } else {
            log.info("Búsqueda rápida: modo PREMIUM — usando API live BDNS");
            candidatasBdns = bdnsClientService.buscarPorFiltros(filtros);
            // Enriquecer con datos del endpoint de detalle (sector real, presupuesto, fechas)
            for (ConvocatoriaDTO dto : candidatasBdns) {
                bdnsClientService.enriquecerConDetalle(dto);
            }
        }

        // 3. Deduplicar y filtrar caducadas
        Map<String, ConvocatoriaDTO> candidatasUnicas = deduplicarYFiltrar(candidatasBdns);
        log.info("Búsqueda rápida: {} candidatas únicas de BDNS", candidatasUnicas.size());

        if (candidatasUnicas.isEmpty()) {
            return 0;
        }

        // 4. Pre-filtro geográfico (safety net) — solo datos del proyecto
        List<ConvocatoriaDTO> filtradas = aplicarFiltroGeografico(candidatasUnicas, proyecto);

        // 6. Limitar
        List<ConvocatoriaDTO> aGuardar = filtradas.stream()
                .limit(MAX_CANDIDATAS)
                .toList();

        // 7. Limpiar recomendaciones anteriores NO evaluadas por IA
        //    (mantener las que sí fueron analizadas con IA)
        recomendacionRepository.deleteByProyectoIdAndUsadaIaFalse(proyecto.getId());

        // 8. Persistir cada candidata como recomendación sin evaluar
        int guardadas = 0;
        Set<String> idsYaRecomendados = obtenerIdsBdnsRecomendados(proyecto.getId());

        for (ConvocatoriaDTO dto : aGuardar) {
            // No duplicar si ya existe una recomendación IA para esta convocatoria
            if (dto.getIdBdns() != null && idsYaRecomendados.contains(dto.getIdBdns())) {
                continue;
            }

            try {
                Convocatoria conv = persistirConvocatoria(dto);
                Recomendacion rec = Recomendacion.builder()
                        .proyecto(proyecto)
                        .convocatoria(conv)
                        .puntuacion(0)
                        .explicacion("Candidata encontrada por búsqueda directa en BDNS. "
                                + "Pulsa 'Analizar con IA' para obtener puntuación y guía detallada.")
                        .usadaIa(false)
                        .build();
                recomendacionRepository.save(rec);
                guardadas++;
            } catch (Exception e) {
                log.warn("Error guardando candidata '{}': {}", dto.getTitulo(), e.getMessage());
            }
        }

        log.info("Búsqueda rápida completada: proyecto={} guardadas={}", proyecto.getId(), guardadas);
        return guardadas;
    }

    /**
     * Obtiene los idBdns de convocatorias que ya tienen recomendación para este proyecto.
     */
    private Set<String> obtenerIdsBdnsRecomendados(Long proyectoId) {
        List<Recomendacion> existentes = recomendacionRepository.findByProyectoId(proyectoId);
        Set<String> ids = new HashSet<>();
        for (Recomendacion r : existentes) {
            if (r.getConvocatoria() != null && r.getConvocatoria().getIdBdns() != null) {
                ids.add(r.getConvocatoria().getIdBdns());
            }
        }
        return ids;
    }

    private Map<String, ConvocatoriaDTO> deduplicarYFiltrar(List<ConvocatoriaDTO> candidatas) {
        Map<String, ConvocatoriaDTO> resultado = new LinkedHashMap<>();
        Set<String> idsBdnsVistos = new HashSet<>();
        for (ConvocatoriaDTO dto : candidatas) {
            if (dto.getTitulo() == null) continue;
            if (dto.getIdBdns() != null && !dto.getIdBdns().isBlank()) {
                if (idsBdnsVistos.contains(dto.getIdBdns())) continue;
                idsBdnsVistos.add(dto.getIdBdns());
            }
            if (resultado.containsKey(dto.getTitulo())) continue;
            // NO descartar caducadas: se conservan para mostrar separadas (vigentes vs no vigentes)
            resultado.put(dto.getTitulo(), dto);
        }
        return resultado;
    }

    private List<ConvocatoriaDTO> aplicarFiltroGeografico(
            Map<String, ConvocatoriaDTO> candidatasUnicas, Proyecto proyecto) {
        String ubicacion = proyecto.getUbicacion();
        if (ubicacion != null && !ubicacion.isBlank()) {
            final String ubiFinal = ubicacion.toLowerCase().trim();
            return candidatasUnicas.values().stream()
                    .filter(dto -> {
                        String ubiConv = dto.getUbicacion();
                        if (ubiConv == null || ubiConv.isBlank() || "Nacional".equalsIgnoreCase(ubiConv)) {
                            return true;
                        }
                        return ubiConv.toLowerCase().contains(ubiFinal)
                                || ubiFinal.contains(ubiConv.toLowerCase());
                    })
                    .toList();
        }
        return new ArrayList<>(candidatasUnicas.values());
    }

    /**
     * Persiste una convocatoria si no existe ya (por título + fuente).
     */
    private Convocatoria persistirConvocatoria(ConvocatoriaDTO dto) {
        return convocatoriaRepository
                .findByTituloIgnoreCaseAndFuente(dto.getTitulo(), dto.getFuente())
                .orElseGet(() -> convocatoriaRepository.save(
                        Convocatoria.builder()
                                .titulo(dto.getTitulo())
                                .tipo(dto.getTipo())
                                .sector(dto.getSector())
                                .ubicacion(dto.getUbicacion())
                                .urlOficial(dto.getUrlOficial())
                                .fuente(dto.getFuente())
                                .idBdns(dto.getIdBdns())
                                .numeroConvocatoria(dto.getNumeroConvocatoria())
                                .fechaCierre(dto.getFechaCierre())
                                .mrr(Boolean.TRUE.equals(dto.getMrr()))
                                .presupuesto(dto.getPresupuesto())
                                .abierto(dto.getAbierto())
                                .finalidad(dto.getFinalidad())
                                .fechaInicio(dto.getFechaInicio())
                                .organismo(dto.getOrganismo())
                                .fechaPublicacion(dto.getFechaPublicacion())
                                .descripcion(dto.getDescripcion())
                                .textoCompleto(dto.getTextoCompleto())
                                .build()));
    }
}

