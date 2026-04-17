package com.syntia.ai.service;

import com.syntia.ai.model.Region;
import com.syntia.ai.model.dto.RegionNodoDTO;
import com.syntia.ai.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;
    private final BdnsClientService bdnsClientService;

    /** ID raíz de España en el catálogo BDNS. */
    private static final Long ID_ESPANA = 1L;

    /**
     * Descarga el catálogo completo de regiones desde la API BDNS y lo guarda en BD.
     *
     * @return número de regiones guardadas
     */
    @Transactional
    public int sincronizarRegiones() {
        List<BdnsClientService.RegionItem> items = bdnsClientService.fetchRegiones();

        List<Region> regiones = items.stream()
                .map(item -> Region.builder()
                        .id(item.id())
                        .descripcion(item.descripcion())
                        .parentId(item.parentId())
                        .build())
                .toList();

        regionRepository.saveAll(regiones);
        log.info("Regiones sincronizadas: {} registros guardados en BD", regiones.size());
        return regiones.size();
    }

    public long count() {
        return regionRepository.count();
    }

    /**
     * Devuelve el conjunto de IDs del nodo dado y todos sus descendientes.
     * Carga todas las regiones una sola vez y recorre en memoria (BFS).
     * Usado para expandir un filtro de región al buscar convocatorias.
     *
     * @param regionId ID del nodo raíz de la búsqueda
     * @return set con el ID del nodo y los de todos sus hijos/nietos
     */
    public Set<Integer> obtenerDescendientesIds(Long regionId) {
        List<Region> todas = regionRepository.findAll();

        // Mapa parentId → lista de hijos
        Map<Long, List<Long>> hijos = new HashMap<>();
        for (Region r : todas) {
            if (r.getParentId() != null) {
                hijos.computeIfAbsent(r.getParentId(), k -> new ArrayList<>()).add(r.getId());
            }
        }

        Set<Integer> resultado = new HashSet<>();
        Queue<Long> cola = new ArrayDeque<>();
        cola.add(regionId);
        while (!cola.isEmpty()) {
            Long actual = cola.poll();
            resultado.add(actual.intValue());
            hijos.getOrDefault(actual, List.of()).forEach(cola::add);
        }
        return resultado;
    }

    /**
     * Devuelve el árbol de regiones de España como lista de nodos raíz con sus hijos.
     * Solo incluye nodos bajo el ID de España para no exponer regiones europeas.
     */
    public List<RegionNodoDTO> obtenerArbolEspana() {
        List<Region> todas = regionRepository.findAll();

        Map<Long, List<Region>> hijosPorPadre = new HashMap<>();
        for (Region r : todas) {
            if (r.getParentId() != null) {
                hijosPorPadre.computeIfAbsent(r.getParentId(), k -> new ArrayList<>()).add(r);
            }
        }

        // Buscar el nodo raíz España
        Optional<Region> espana = todas.stream().filter(r -> ID_ESPANA.equals(r.getId())).findFirst();
        if (espana.isEmpty()) return List.of();

        // Devolver solo los hijos directos de España (macro-regiones) con su árbol completo
        List<Region> macroRegiones = hijosPorPadre.getOrDefault(ID_ESPANA, List.of());
        return macroRegiones.stream()
                .map(r -> construirNodo(r, hijosPorPadre))
                .toList();
    }

    private RegionNodoDTO construirNodo(Region region, Map<Long, List<Region>> hijosPorPadre) {
        List<RegionNodoDTO> hijos = hijosPorPadre.getOrDefault(region.getId(), List.of())
                .stream()
                .map(hijo -> construirNodo(hijo, hijosPorPadre))
                .toList();
        return new RegionNodoDTO(region.getId(), region.getDescripcion(), hijos);
    }
}
