package com.syntia.ai.controller.api;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.dto.ConvocatoriaDetalleDTO;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import com.syntia.ai.model.dto.RegionNodoDTO;
import com.syntia.ai.repository.*;
import com.syntia.ai.service.RegionService;
import com.syntia.ai.service.UbicacionNormalizador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Endpoints públicos de convocatorias: búsqueda y destacadas para el Home.
 * No requieren autenticación; el detalle completo sí la requiere (gestionado en front).
 */
@RestController
@RequestMapping("/api/convocatorias/publicas")
public class ConvocatoriaPublicaController {

    private final ConvocatoriaRepository convocatoriaRepository;
    private final IdxConvocatoriaBeneficiarioRepository beneficiarioRepository;
    private final IdxConvocatoriaFinalidadRepository finalidadIdxRepository;
    private final IdxConvocatoriaInstrumentoRepository instrumentoIdxRepository;
    private final IdxConvocatoriaOrganoRepository organoIdxRepository;
    private final IdxConvocatoriaRegionRepository regionIdxRepository;
    private final IdxConvocatoriaTipoAdminRepository tipoAdminIdxRepository;
    private final IdxConvocatoriaActividadRepository actividadIdxRepository;
    private final IdxConvocatoriaReglamentoRepository reglamentoIdxRepository;
    private final IdxConvocatoriaObjetivoRepository objetivoIdxRepository;
    private final IdxConvocatoriaSectorProductoRepository sectorProductoIdxRepository;
    private final RegionService regionService;

    public ConvocatoriaPublicaController(ConvocatoriaRepository convocatoriaRepository,
                                         IdxConvocatoriaBeneficiarioRepository beneficiarioRepository,
                                         IdxConvocatoriaFinalidadRepository finalidadIdxRepository,
                                         IdxConvocatoriaInstrumentoRepository instrumentoIdxRepository,
                                         IdxConvocatoriaOrganoRepository organoIdxRepository,
                                         IdxConvocatoriaRegionRepository regionIdxRepository,
                                         IdxConvocatoriaTipoAdminRepository tipoAdminIdxRepository,
                                         IdxConvocatoriaActividadRepository actividadIdxRepository,
                                         IdxConvocatoriaReglamentoRepository reglamentoIdxRepository,
                                         IdxConvocatoriaObjetivoRepository objetivoIdxRepository,
                                         IdxConvocatoriaSectorProductoRepository sectorProductoIdxRepository,
                                         RegionService regionService) {
        this.convocatoriaRepository = convocatoriaRepository;
        this.beneficiarioRepository = beneficiarioRepository;
        this.finalidadIdxRepository = finalidadIdxRepository;
        this.instrumentoIdxRepository = instrumentoIdxRepository;
        this.organoIdxRepository = organoIdxRepository;
        this.regionIdxRepository = regionIdxRepository;
        this.tipoAdminIdxRepository = tipoAdminIdxRepository;
        this.actividadIdxRepository = actividadIdxRepository;
        this.reglamentoIdxRepository = reglamentoIdxRepository;
        this.objetivoIdxRepository = objetivoIdxRepository;
        this.sectorProductoIdxRepository = sectorProductoIdxRepository;
        this.regionService = regionService;
    }

    /**
     * Búsqueda pública full-text con paginación.
     * Parámetros: q (keyword), sector (filtro), page, size.
     */
    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "") String sector,
            @RequestParam(required = false, defaultValue = "") String tipo,
            @RequestParam(required = false) Boolean abierto,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String ubicacion,
            @RequestParam(required = false) Double presupuestoMin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);

        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));

        // Fallback temporal para compatibilidad con el front estándar que aún envía ubicación en texto
        if (regionId == null && ubicacion != null && !ubicacion.isBlank()) {
            Integer mappedId = UbicacionNormalizador.normalizarARegionId(ubicacion);
            if (mappedId != null) {
                regionId = mappedId.longValue();
            }
        }

        // Si se especifica región, expandir a todos los descendientes
        boolean filtrarRegion = regionId != null;
        Set<Integer> regionIds = filtrarRegion
                ? regionService.obtenerDescendientesIds(regionId)
                : Set.of();

        Page<Convocatoria> resultado = convocatoriaRepository.buscarPublicoConRegion(
                q.isBlank() ? null : q,
                sector.isBlank() ? null : sector,
                tipo.isBlank() ? null : tipo,
                abierto == null || !abierto,
                filtrarRegion,
                regionIds,
                presupuestoMin != null && presupuestoMin > 0 ? presupuestoMin : null,
                pageRequest
        );

        Map<String, List<String>> beneficiarioMap = cargarBeneficiarios(resultado.getContent());
        Page<ConvocatoriaPublicaDTO> dtos = resultado.map(c -> toPublicDTO(c, beneficiarioMap));
        return ResponseEntity.ok(Map.of(
                "content", dtos.getContent(),
                "totalElements", dtos.getTotalElements(),
                "totalPages", dtos.getTotalPages(),
                "page", dtos.getNumber(),
                "size", dtos.getSize()
        ));
    }

    /**
     * Árbol de regiones de España para el selector de filtro avanzado.
     * Devuelve macro-regiones → CCAA → provincias.
     * Requiere haber sincronizado regiones previamente (POST /api/admin/regiones/sync).
     */
    @GetMapping("/regiones")
    public ResponseEntity<List<RegionNodoDTO>> regiones() {
        return ResponseEntity.ok(regionService.obtenerArbolEspana());
    }

    /**
     * Devuelve los valores distintos de finalidad presentes en la BD, ordenados alfabéticamente.
     * Usado para poblar dinámicamente el selector de sector en el frontend.
     */
    @GetMapping("/finalidades")
    public ResponseEntity<List<String>> finalidades() {
        return ResponseEntity.ok(convocatoriaRepository.findFinalidadesDistintas());
    }

    /**
     * Devuelve los valores distintos de tipo presentes en la BD, ordenados alfabéticamente.
     * Usado para poblar dinámicamente el selector de nivel en el frontend.
     */
    @GetMapping("/tipos")
    public ResponseEntity<List<String>> tipos() {
        return ResponseEntity.ok(convocatoriaRepository.findTiposDistintos());
    }

    /**
     * Detalle completo de una convocatoria con datos de catálogos BDNS.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        return convocatoriaRepository.findById(id)
                .map(c -> ResponseEntity.ok(toDetalleDTO(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Últimas convocatorias para la sección de destacadas del Home.
     * Devuelve hasta 16 convocatorias recientes.
     */
    @GetMapping("/destacadas")
    public ResponseEntity<List<ConvocatoriaPublicaDTO>> destacadas() {
        List<Convocatoria> recientes = convocatoriaRepository.findTop16ByAbiertoTrueOrderByIdDesc();
        Map<String, List<String>> beneficiarioMap = cargarBeneficiarios(recientes);
        List<ConvocatoriaPublicaDTO> dtos = recientes.stream().map(c -> toPublicDTO(c, beneficiarioMap)).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Carga los tipos de beneficiario para una lista de convocatorias en una sola query.
     */
    private Map<String, List<String>> cargarBeneficiarios(List<Convocatoria> convocatorias) {
        Set<String> numeros = new HashSet<>();
        for (Convocatoria c : convocatorias) {
            if (c.getNumeroConvocatoria() != null) numeros.add(c.getNumeroConvocatoria());
        }
        if (numeros.isEmpty()) return Map.of();

        Map<String, List<String>> map = new HashMap<>();
        for (Object[] row : beneficiarioRepository.findBeneficiariosByNumeros(numeros)) {
            String num = (String) row[0];
            String desc = (String) row[1];
            map.computeIfAbsent(num, k -> new ArrayList<>()).add(desc);
        }
        return map;
    }

    private ConvocatoriaPublicaDTO toPublicDTO(Convocatoria c, Map<String, List<String>> beneficiarioMap) {
        String url = construirUrl(c);
        List<String> beneficiarios = c.getNumeroConvocatoria() != null
                ? beneficiarioMap.getOrDefault(c.getNumeroConvocatoria(), List.of())
                : List.of();
        return ConvocatoriaPublicaDTO.builder()
                .id(c.getId())
                .titulo(c.getTitulo())
                .tipo(c.getTipo())
                .sector(c.getSector())
                .organismo(c.getOrganismo())
                .ubicacion(c.getUbicacion())
                .fechaCierre(c.getFechaCierre())
                .fechaPublicacion(c.getFechaPublicacion())
                .abierto(c.getAbierto())
                .urlOficial(url)
                .idBdns(c.getIdBdns())
                .numeroConvocatoria(c.getNumeroConvocatoria())
                .presupuesto(c.getPresupuesto())
                .regionId(c.getRegionId())
                .provinciaId(c.getProvinciaId())
                .tiposBeneficiario(beneficiarios)
                .build();
    }

    private ConvocatoriaDetalleDTO toDetalleDTO(Convocatoria c) {
        String num = c.getNumeroConvocatoria();
        boolean hasNum = num != null && !num.isBlank();

        List<String> beneficiarios = hasNum
                ? beneficiarioRepository.findBeneficiariosByNumeros(Set.of(num)).stream()
                    .map(row -> (String) row[1]).toList()
                : List.of();
        List<String> finalidades = hasNum ? finalidadIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();
        List<String> instrumentos = hasNum ? instrumentoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();
        List<String> organos = hasNum ? organoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();
        List<String> regiones = hasNum ? regionIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();
        List<String> tiposAdmin = hasNum ? tipoAdminIdxRepository.findTiposAdminByNumeroConvocatoria(num) : List.of();
        List<String> actividades = hasNum ? actividadIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();
        List<String> reglamentos = hasNum ? reglamentoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();
        List<String> objetivos = hasNum ? objetivoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();
        List<String> sectoresProducto = hasNum ? sectorProductoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of();

        return ConvocatoriaDetalleDTO.builder()
                .id(c.getId())
                .titulo(c.getTitulo())
                .tipo(c.getTipo())
                .sector(c.getSector())
                .ubicacion(c.getUbicacion())
                .organismo(c.getOrganismo())
                .descripcion(c.getDescripcion())
                .textoCompleto(c.getTextoCompleto())
                .urlOficial(construirUrl(c))
                .idBdns(c.getIdBdns())
                .numeroConvocatoria(c.getNumeroConvocatoria())
                .finalidad(c.getFinalidad())
                .presupuesto(c.getPresupuesto())
                .abierto(c.getAbierto())
                .mrr(c.getMrr())
                .fechaCierre(c.getFechaCierre())
                .fechaPublicacion(c.getFechaPublicacion())
                .fechaInicio(c.getFechaInicio())
                .regionId(c.getRegionId())
                .provinciaId(c.getProvinciaId())
                .tiposBeneficiario(beneficiarios)
                .finalidades(finalidades)
                .instrumentos(instrumentos)
                .organos(organos)
                .regiones(regiones)
                .tipoAdmin(tiposAdmin.isEmpty() ? null : String.join(", ", tiposAdmin))
                .actividades(actividades)
                .reglamentos(reglamentos)
                .objetivos(objetivos)
                .sectoresProducto(sectoresProducto)
                .build();
    }

    private String construirUrl(Convocatoria c) {
        if (c.getNumeroConvocatoria() != null && !c.getNumeroConvocatoria().isBlank()) {
            return "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias?numConv=" + c.getNumeroConvocatoria();
        }
        if (c.getIdBdns() != null && !c.getIdBdns().isBlank()) {
            return "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/" + c.getIdBdns();
        }
        String url = c.getUrlOficial();
        if (url != null) {
            url = url.replace("/bdnstrans/GE/es/convocatoria/", "/bdnstrans/GE/es/convocatorias/");
        }
        return url;
    }
}
