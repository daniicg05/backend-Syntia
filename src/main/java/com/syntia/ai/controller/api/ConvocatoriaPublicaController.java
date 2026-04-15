package com.syntia.ai.controller.api;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.dto.ConvocatoriaDetalleDTO;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.service.BdnsClientService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Endpoints públicos de convocatorias: búsqueda y destacadas para el Home.
 * No requieren autenticación, incluido el detalle para la ficha interna del frontend.
 */
@RestController
@RequestMapping("/api/convocatorias/publicas")
public class ConvocatoriaPublicaController {

    private final ConvocatoriaRepository convocatoriaRepository;
    private final BdnsClientService bdnsClientService;

    public ConvocatoriaPublicaController(ConvocatoriaRepository convocatoriaRepository,
                                         BdnsClientService bdnsClientService) {
        this.convocatoriaRepository = convocatoriaRepository;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);

        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<Convocatoria> resultado = convocatoriaRepository.buscarPublico(
                q.isBlank() ? null : q,
                sector.isBlank() ? null : sector,
                tipo.isBlank() ? null : tipo,
                abierto == null || !abierto,   // incluirCerradas: true cuando abierto=null o false
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
     * Últimas convocatorias para la sección de destacadas del Home.
     * Devuelve hasta 16 convocatorias recientes.
     */
    @GetMapping("/destacadas")
    public ResponseEntity<List<ConvocatoriaPublicaDTO>> destacadas() {
        List<Convocatoria> recientes = convocatoriaRepository.findTop16ByAbiertoTrueOrderByIdDesc();
        List<ConvocatoriaPublicaDTO> dtos = recientes.stream().map(this::toPublicDTO).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Detalle público para la vista interna de convocatoria en frontend.
     * Devuelve datos de BD local y completa con BDNS cuando hay numeroConvocatoria.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConvocatoriaDetalleDTO> detalle(@PathVariable Long id) {
        Convocatoria c = convocatoriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Convocatoria no encontrada: " + id));

        String codigoBdns = c.getNumeroConvocatoria();
        if (codigoBdns == null || codigoBdns.isBlank()) {
            codigoBdns = c.getIdBdns();
        }

        String sector = c.getSector();
        String descripcion = c.getDescripcion();
        List<String> tiposBeneficiario = new ArrayList<>();

        if (c.getNumeroConvocatoria() != null && !c.getNumeroConvocatoria().isBlank()) {
            BdnsClientService.DetalleConvocatoriaBdns detalleBdns =
                    bdnsClientService.obtenerDetalleConvocatoria(c.getNumeroConvocatoria());
            if (detalleBdns != null) {
                if ((sector == null || sector.isBlank()) && detalleBdns.sector() != null && !detalleBdns.sector().isBlank()) {
                    sector = detalleBdns.sector();
                }
                if ((descripcion == null || descripcion.isBlank())
                        && detalleBdns.descripcion() != null
                        && !detalleBdns.descripcion().isBlank()) {
                    descripcion = detalleBdns.descripcion();
                }
                if (detalleBdns.tiposBeneficiario() != null && !detalleBdns.tiposBeneficiario().isEmpty()) {
                    tiposBeneficiario = detalleBdns.tiposBeneficiario();
                }
            }
        }

        ConvocatoriaDetalleDTO dto = ConvocatoriaDetalleDTO.builder()
                .id(c.getId())
                .codigoBdns(codigoBdns)
                .sector(sector)
                .descripcion(descripcion)
                .tiposBeneficiario(tiposBeneficiario)
                .build();
        return ResponseEntity.ok(dto);
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
