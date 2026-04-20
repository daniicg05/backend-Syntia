package com.syntia.ai.controller.api;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.dto.ConvocatoriaDetalleDTO;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import com.syntia.ai.model.dto.RegionNodoDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.service.RegionService;
import com.syntia.ai.service.UbicacionNormalizador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.syntia.ai.util.JsonListParser;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Endpoints públicos de convocatorias: búsqueda y destacadas para el Home.
 * No requieren autenticación; el detalle completo sí la requiere (gestionado en front).
 */
@RestController
@RequestMapping("/api/convocatorias/publicas")
public class ConvocatoriaPublicaController {

    private final ConvocatoriaRepository convocatoriaRepository;
    private final RegionService regionService;

    public ConvocatoriaPublicaController(ConvocatoriaRepository convocatoriaRepository,
                                         RegionService regionService) {
        this.convocatoriaRepository = convocatoriaRepository;
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
                pageRequest
        );

        Page<ConvocatoriaPublicaDTO> dtos = resultado.map(this::toPublicDTO);
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
     * Detalle público de una convocatoria por número de convocatoria.
     */
    @GetMapping("/{numeroConvocatoria}")
    public ResponseEntity<ConvocatoriaDetalleDTO> detalle(@PathVariable String numeroConvocatoria) {
        return convocatoriaRepository.findByNumeroConvocatoria(numeroConvocatoria)
                .map(c -> ResponseEntity.ok(toDetalleDTO(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    private ConvocatoriaDetalleDTO toDetalleDTO(Convocatoria c) {
        String codigoBdns = c.getNumeroConvocatoria() != null && !c.getNumeroConvocatoria().isBlank()
                ? c.getNumeroConvocatoria() : c.getIdBdns();

        List<ConvocatoriaDetalleDTO.TipoBeneficiarioDTO> tiposBenef =
                JsonListParser.parse(c.getTiposBeneficiarios(),
                        new TypeReference<List<ConvocatoriaDetalleDTO.TipoBeneficiarioDTO>>() {});

        List<String> tiposBenefLegacy = tiposBenef.stream()
                .map(ConvocatoriaDetalleDTO.TipoBeneficiarioDTO::getDescripcion)
                .filter(d -> d != null && !d.isBlank())
                .toList();

        return ConvocatoriaDetalleDTO.builder()
                // Legacy
                .id(c.getId())
                .codigoBdns(codigoBdns)
                .descripcion(c.getDescripcion())
                .tiposBeneficiario(tiposBenefLegacy)

                // Identificación
                .idBdns(c.getIdBdns())
                .numeroConvocatoria(c.getNumeroConvocatoria())
                .titulo(c.getTitulo())

                // Clasificación
                .tipo(c.getTipo())
                .ubicacion(c.getUbicacion())
                .sector(c.getSector())
                .finalidad(c.getFinalidad())

                // Organismo
                .nivel1(c.getOrganoNivel1())
                .nivel2(c.getOrganoNivel2())
                .nivel3(c.getOrganoNivel3())
                .fuente(c.getFuente())

                // Financiero
                .presupuestoTotal(c.getPresupuesto())
                .ayudaEstado(c.getAyudaEstado())
                .urlAyudaEstado(c.getUrlAyudaEstado())
                .mrr(c.getMrr())

                // Plazos
                .fechaRecepcion(c.getFechaRecepcion())
                .fechaInicioSolicitud(c.getFechaInicioSolicitud())
                .fechaFinSolicitud(c.getFechaFinSolicitud())
                .fechaCierre(c.getFechaCierre())
                .textInicio(c.getTextInicio())
                .textFin(c.getTextFin())

                // Procedimiento / Bases
                .basesReguladoras(c.getDescripcionBasesReguladoras())
                .descripcionBasesReguladoras(c.getDescripcionBasesReguladoras())
                .urlBasesReguladoras(c.getUrlBasesReguladoras())
                .sePublicaDiarioOficial(c.getSePublicaDiarioOficial())
                .sedeElectronica(c.getSedeElectronica())
                .tipoConvocatoria(c.getTipoConvocatoria())
                .urlOficial(construirUrl(c))

                // Reglamento UE
                .reglamentoDescripcion(c.getReglamentoDescripcion())
                .reglamentoAutorizacion(c.getReglamentoAutorizacion())
                .advertencia(c.getAdvertencia())

                // Arrays BDNS
                .instrumentos(JsonListParser.parseStringList(c.getInstrumentos()))
                .tiposBeneficiarios(tiposBenef)
                .sectores(JsonListParser.parse(c.getSectores(),
                        new TypeReference<List<ConvocatoriaDetalleDTO.SectorDTO>>() {}))
                .sectoresProductos(JsonListParser.parse(c.getSectoresProductos(),
                        new TypeReference<List<ConvocatoriaDetalleDTO.SectorDTO>>() {}))
                .regiones(JsonListParser.parseStringList(c.getRegiones()))
                .fondos(JsonListParser.parseStringList(c.getFondos()))
                .objetivos(JsonListParser.parseStringList(c.getObjetivos()))
                .anuncios(JsonListParser.parse(c.getAnuncios(),
                        new TypeReference<List<ConvocatoriaDetalleDTO.AnuncioDTO>>() {}))
                .documentos(JsonListParser.parse(c.getDocumentos(),
                        new TypeReference<List<ConvocatoriaDetalleDTO.DocumentoDTO>>() {}))

                .build();
    }

    /**
     * Últimas convocatorias para la sección de destacadas del Home.
     * Devuelve hasta 16 convocatorias recientes.
     */
    @GetMapping("/destacadas")
    public ResponseEntity<List<ConvocatoriaPublicaDTO>> destacadas() {
        List<Convocatoria> recientes = convocatoriaRepository.findTop16ByAbiertoTrueOrderByIdDesc();
        List<ConvocatoriaPublicaDTO> dtos = recientes.stream().map(this::toPublicDTO).toList();
        return ResponseEntity.ok(dtos);
    }

    private ConvocatoriaPublicaDTO toPublicDTO(Convocatoria c) {
        String url = construirUrl(c);
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