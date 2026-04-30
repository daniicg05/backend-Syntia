package com.syntia.ai.controller.api;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.dto.ConvocatoriaDetalleDTO;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import com.syntia.ai.model.dto.DocumentoBdnsDTO;
import com.syntia.ai.model.dto.RegionNodoDTO;
import com.syntia.ai.repository.*;
import com.syntia.ai.service.BdnsClientService;
import com.syntia.ai.service.RegionService;
import com.syntia.ai.service.UbicacionNormalizador;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Endpoints públicos de convocatorias: búsqueda y destacadas para el Home.
 * No requieren autenticación; el detalle completo sí la requiere (gestionado en front).
 */
@Slf4j
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
    private final BdnsClientService bdnsClientService;

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
                                         RegionService regionService,
                                         BdnsClientService bdnsClientService) {
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
        this.bdnsClientService = bdnsClientService;
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
            @RequestParam(required = false) Integer plazoCierreDias,
            @RequestParam(required = false, defaultValue = "") String tipoBeneficiario,
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
        LocalDate fechaCierreHasta = plazoCierreDias != null && plazoCierreDias > 0
                ? LocalDate.now().plusDays(plazoCierreDias)
                : null;

        Page<Convocatoria> resultado = convocatoriaRepository.buscarPublicoConRegion(
                q.isBlank() ? null : q,
                sector.isBlank() ? null : sector,
                tipo.isBlank() ? null : tipo,
                abierto == null || !abierto,
                filtrarRegion,
                regionIds,
                presupuestoMin != null && presupuestoMin > 0 ? presupuestoMin : null,
                fechaCierreHasta,
                tipoBeneficiario.isBlank() ? null : tipoBeneficiario,
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
     * Detalle completo de una convocatoria.
     * Carga datos locales (BD + catálogos indexados) y datos live de la API BDNS,
     * y para cada campo elige la fuente más rica.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        Optional<Convocatoria> opt = convocatoriaRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Convocatoria c = opt.get();
        String numConv = c.getNumeroConvocatoria();

        // Siempre obtener datos locales de catálogos indexados
        LocalData local = cargarDatosLocales(c);

        // Intentar enriquecer con API live
        Map<String, Object> live = null;
        if (numConv != null && !numConv.isBlank()) {
            live = bdnsClientService.obtenerDetalleLive(numConv);
        }

        return ResponseEntity.ok(buildDetalleMerged(c, local, live));
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
        Boolean abierto = calcularAbierto(c.getAbierto(), c.getFechaCierre());
        return ConvocatoriaPublicaDTO.builder()
                .id(c.getId())
                .titulo(c.getTitulo())
                .tipo(c.getTipo())
                .sector(c.getSector())
                .organismo(c.getOrganismo())
                .ubicacion(c.getUbicacion())
                .fechaCierre(c.getFechaCierre())
                .fechaPublicacion(c.getFechaPublicacion())
                .abierto(abierto)
                .urlOficial(url)
                .idBdns(c.getIdBdns())
                .numeroConvocatoria(c.getNumeroConvocatoria())
                .presupuesto(c.getPresupuesto())
                .regionId(c.getRegionId())
                .provinciaId(c.getProvinciaId())
                .tiposBeneficiario(beneficiarios)
                .build();
    }

    // ── Datos locales de catálogos indexados ─────────────────────────────

    private record LocalData(
            List<String> beneficiarios, List<String> finalidades,
            List<String> instrumentos, List<String> organos,
            List<String> regiones, List<String> tiposAdmin,
            List<String> actividades, List<String> reglamentos,
            List<String> objetivos, List<String> sectoresProducto) {}

    private LocalData cargarDatosLocales(Convocatoria c) {
        String num = c.getNumeroConvocatoria();
        boolean hasNum = num != null && !num.isBlank();
        return new LocalData(
                hasNum ? beneficiarioRepository.findBeneficiariosByNumeros(Set.of(num)).stream()
                        .map(row -> (String) row[1]).toList() : List.of(),
                hasNum ? finalidadIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of(),
                hasNum ? instrumentoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of(),
                hasNum ? organoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of(),
                hasNum ? regionIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of(),
                hasNum ? tipoAdminIdxRepository.findTiposAdminByNumeroConvocatoria(num) : List.of(),
                hasNum ? actividadIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of(),
                hasNum ? reglamentoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of(),
                hasNum ? objetivoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of(),
                hasNum ? sectorProductoIdxRepository.findDescripcionesByNumeroConvocatoria(num) : List.of()
        );
    }

    // ── Merge: para cada campo elige la fuente más rica ────────────────

    @SuppressWarnings("unchecked")
    private ConvocatoriaDetalleDTO buildDetalleMerged(Convocatoria c, LocalData local, Map<String, Object> live) {
        boolean hasLive = live != null;

        // --- Datos live extraídos (vacíos si no hay live) ---
        Map<String, Object> organo = Map.of();
        List<String> liveInstrumentos = List.of(), liveBeneficiarios = List.of(),
                     liveSectores = List.of(), liveRegiones = List.of(),
                     liveObjetivos = List.of(), liveSectoresProducto = List.of(),
                     liveFondos = List.of(), liveAnuncios = List.of();
        List<DocumentoBdnsDTO> documentos = List.of();
        String organoNivel1 = null, organoNivel2 = null, organoNivel3 = null;

        if (hasLive) {
            organo = live.get("organo") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            organoNivel1 = organo.getOrDefault("nivel1", "").toString();
            organoNivel2 = organo.getOrDefault("nivel2", "").toString();
            organoNivel3 = organo.getOrDefault("nivel3", "").toString();
            if (organoNivel1.isBlank()) organoNivel1 = null;
            if (organoNivel2.isBlank()) organoNivel2 = null;
            if (organoNivel3.isBlank()) organoNivel3 = null;

            liveInstrumentos = extraerDescripciones(live.get("instrumentos"));
            liveBeneficiarios = extraerDescripciones(live.get("tiposBeneficiarios"));
            liveSectores = extraerDescripciones(live.get("sectores"));
            liveRegiones = extraerDescripciones(live.get("regiones"));
            liveObjetivos = extraerDescripciones(live.get("objetivos"));
            liveSectoresProducto = extraerDescripciones(live.get("sectoresProductos"));
            liveFondos = extraerDescripciones(live.get("fondos"));
            liveAnuncios = extraerDescripciones(live.get("anuncios"));
            documentos = extraerDocumentos(live.get("documentos"));
        }

        // --- Órganos live como lista (para comparar con local.organos) ---
        List<String> liveOrganos = new ArrayList<>();
        if (organoNivel3 != null) liveOrganos.add(organoNivel3);
        if (organoNivel2 != null && !liveOrganos.contains(organoNivel2)) liveOrganos.add(organoNivel2);

        // --- Merge: para listas, quedarse con la que tenga más elementos ---
        List<String> tiposBeneficiario = richer(local.beneficiarios(), liveBeneficiarios);
        List<String> finalidades = local.finalidades();
        List<String> instrumentos = richer(local.instrumentos(), liveInstrumentos);
        List<String> organos = richer(local.organos(), liveOrganos);
        List<String> regiones = richer(local.regiones(), liveRegiones);
        List<String> actividades = local.actividades();
        List<String> reglamentos = local.reglamentos();
        List<String> objetivos = richer(local.objetivos(), liveObjetivos);
        List<String> sectoresProducto = richer(local.sectoresProducto(), liveSectoresProducto);

        // --- Merge: para strings, preferir live si aporta más que local ---
        String titulo = pick(hasLive ? toStr(live.get("descripcion")) : null, c.getTitulo());
        String descripcion = pick(hasLive ? toStr(live.get("descripcion")) : null, c.getDescripcion());
        String sector = !liveSectores.isEmpty() ? String.join(", ", liveSectores) : c.getSector();
        String organismo = pick(organoNivel2, c.getOrganismo());
        String finalidad = pick(hasLive ? toStr(live.get("descripcionFinalidad")) : null, c.getFinalidad());
        String tipoAdmin = local.tiposAdmin().isEmpty()
                ? organoNivel1 : String.join(", ", local.tiposAdmin());
        Double presupuesto = hasLive && live.get("presupuestoTotal") instanceof Number n
                ? n.doubleValue() : c.getPresupuesto();
        LocalDate fechaCierreMerged = c.getFechaCierre();
        if (fechaCierreMerged == null && hasLive) {
            fechaCierreMerged = parseDate(live.get("fechaFinSolicitud"));
        }
        Boolean abiertoRaw = hasLive && live.get("abierto") instanceof Boolean b ? b : c.getAbierto();
        Boolean abierto = calcularAbierto(abiertoRaw, fechaCierreMerged);
        Boolean mrr = hasLive && live.get("mrr") instanceof Boolean b ? b : c.getMrr();

        // Si finalidades local viene vacío pero tenemos finalidad de live, poblarla
        if (finalidades.isEmpty() && finalidad != null) {
            finalidades = List.of(finalidad);
        }
        // Si sectoresProducto quedó vacío, usar sectores live como fallback
        if (sectoresProducto.isEmpty() && !liveSectores.isEmpty()) {
            sectoresProducto = liveSectores;
        }

        return ConvocatoriaDetalleDTO.builder()
                .id(c.getId())
                .titulo(titulo)
                .tipo(c.getTipo())
                .sector(sector)
                .ubicacion(c.getUbicacion())
                .organismo(organismo)
                .descripcion(descripcion)
                .textoCompleto(c.getTextoCompleto())
                .urlOficial(construirUrl(c))
                .idBdns(hasLive ? toStr(live.get("codigoBDNS")) : c.getIdBdns())
                .numeroConvocatoria(c.getNumeroConvocatoria())
                .finalidad(finalidad)
                .presupuesto(presupuesto)
                .abierto(abierto)
                .mrr(mrr)
                .fechaCierre(fechaCierreMerged)
                .fechaPublicacion(c.getFechaPublicacion())
                .fechaInicio(c.getFechaInicio())
                .regionId(c.getRegionId())
                .provinciaId(c.getProvinciaId())
                // Catálogos (el más rico entre local y live)
                .tiposBeneficiario(tiposBeneficiario)
                .finalidades(finalidades)
                .instrumentos(instrumentos)
                .organos(organos)
                .regiones(regiones)
                .tipoAdmin(tipoAdmin)
                .actividades(actividades)
                .reglamentos(reglamentos)
                .objetivos(objetivos)
                .sectoresProducto(sectoresProducto)
                // Campos solo disponibles en live
                .live(hasLive)
                .organoNivel1(organoNivel1)
                .organoNivel2(organoNivel2)
                .organoNivel3(organoNivel3)
                .tipoConvocatoria(hasLive ? toStr(live.get("tipoConvocatoria")) : null)
                .descripcionBasesReguladoras(hasLive ? toStr(live.get("descripcionBasesReguladoras")) : null)
                .urlBasesReguladoras(hasLive ? toStr(live.get("urlBasesReguladoras")) : null)
                .fechaInicioSolicitud(hasLive ? parseDate(live.get("fechaInicioSolicitud")) : null)
                .fechaFinSolicitud(hasLive ? parseDate(live.get("fechaFinSolicitud")) : null)
                .textInicio(hasLive ? toStr(live.get("textInicio")) : null)
                .textFin(hasLive ? toStr(live.get("textFin")) : null)
                .sePublicaDiarioOficial(hasLive && live.get("sePublicaDiarioOficial") instanceof Boolean b ? b : null)
                .ayudaEstado(hasLive ? toStr(live.get("ayudaEstado")) : null)
                .urlAyudaEstado(hasLive ? toStr(live.get("urlAyudaEstado")) : null)
                .reglamento(hasLive ? toStr(live.get("reglamento")) : null)
                .sedeElectronica(hasLive ? toStr(live.get("sedeElectronica")) : null)
                .fechaRecepcion(hasLive ? toStr(live.get("fechaRecepcion")) : null)
                .documentos(documentos)
                .anuncios(liveAnuncios)
                .fondos(liveFondos)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────��─────────────

    /** Devuelve la lista con más elementos; ante empate prefiere local. */
    private List<String> richer(List<String> local, List<String> live) {
        return live.size() > local.size() ? live : local;
    }

    /** Devuelve el primer string no-blank, o null. */
    private String pick(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return fallback;
    }

    private List<String> extraerDescripciones(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object desc = m.get("descripcion");
                if (desc instanceof String s && !s.isBlank()) result.add(s.strip());
            }
        }
        return result;
    }

    private List<DocumentoBdnsDTO> extraerDocumentos(Object raw) {
        if (!(raw instanceof List<?> docList)) return List.of();
        List<DocumentoBdnsDTO> result = new ArrayList<>();
        for (Object d : docList) {
            if (d instanceof Map<?, ?> dm) {
                result.add(DocumentoBdnsDTO.builder()
                        .id(toLong(dm.get("id")))
                        .descripcion(toStr(dm.get("descripcion")))
                        .nombreFic(toStr(dm.get("nombreFic")))
                        .tamanio(toLong(dm.get("long")))
                        .fechaPublicacion(toStr(dm.get("datPublicacion")))
                        .build());
            }
        }
        return result;
    }

    private String toStr(Object obj) {
        return obj instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private Long toLong(Object obj) {
        return obj instanceof Number n ? n.longValue() : null;
    }

    private LocalDate parseDate(Object obj) {
        if (obj instanceof String s && !s.isBlank()) {
            try { return LocalDate.parse(s); } catch (Exception ignored) {}
        }
        return null;
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

    private Boolean calcularAbierto(Boolean abierto, LocalDate fechaCierre) {
        if (Boolean.TRUE.equals(abierto)) return true;
        if (fechaCierre == null) return true;
        return !fechaCierre.isBefore(LocalDate.now());
    }
}
