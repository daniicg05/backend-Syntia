package com.syntia.ai.controller.api;

import com.syntia.ai.model.AnalisisConvocatoria;
import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.GuiaUsuarioDTO;
import com.syntia.ai.repository.AnalisisConvocatoriaRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.service.GuiaPdfService;
import com.syntia.ai.service.GuiaTranslationService;
import com.syntia.ai.service.OpenAiGuiaService;
import com.syntia.ai.service.UsuarioService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/usuario/guias")
@PreAuthorize("hasRole('USUARIO')")
public class GuiaUsuarioController {

    private static final long ANALISIS_ID_OFFSET = 1_000_000L;

    private final RecomendacionRepository recomendacionRepository;
    private final AnalisisConvocatoriaRepository analisisConvocatoriaRepository;
    private final OpenAiGuiaService openAiGuiaService;
    private final UsuarioService usuarioService;
    private final GuiaPdfService guiaPdfService;
    private final GuiaTranslationService guiaTranslationService;

    public GuiaUsuarioController(RecomendacionRepository recomendacionRepository,
                                 AnalisisConvocatoriaRepository analisisConvocatoriaRepository,
                                 OpenAiGuiaService openAiGuiaService,
                                 UsuarioService usuarioService,
                                 GuiaPdfService guiaPdfService,
                                 GuiaTranslationService guiaTranslationService) {
        this.recomendacionRepository = recomendacionRepository;
        this.analisisConvocatoriaRepository = analisisConvocatoriaRepository;
        this.openAiGuiaService = openAiGuiaService;
        this.usuarioService = usuarioService;
        this.guiaPdfService = guiaPdfService;
        this.guiaTranslationService = guiaTranslationService;
    }

    @GetMapping
    public ResponseEntity<List<GuiaUsuarioDTO>> listarGuias(Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);

        List<GuiaUsuarioDTO> guias = new ArrayList<>();

        // 1. Guías de recomendaciones (flujo proyecto → buscar → analizar IA → ver guía)
        List<Recomendacion> recs = recomendacionRepository.findGuiasEnriquecidasByUsuarioId(usuario.getId());
        for (Recomendacion rec : recs) {
            GuiaSubvencionDTO guiaDto = openAiGuiaService.deserializarGuia(rec.getGuiaEnriquecida());
            if (guiaDto == null) continue;

            String numConv = rec.getConvocatoria().getNumeroConvocatoria();
            String url = (numConv != null && !numConv.isBlank())
                    ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                    : rec.getConvocatoria().getUrlOficial();

            guias.add(GuiaUsuarioDTO.builder()
                    .id(rec.getId())
                    .origen("recomendacion")
                    .convocatoriaId(rec.getConvocatoria().getId())
                    .titulo(rec.getConvocatoria().getTitulo())
                    .organismo(rec.getConvocatoria().getOrganismo())
                    .sector(rec.getConvocatoria().getSector())
                    .ubicacion(rec.getConvocatoria().getUbicacion())
                    .fechaCierre(rec.getConvocatoria().getFechaCierre())
                    .abierto(calcularAbierto(rec.getConvocatoria().getAbierto(), rec.getConvocatoria().getFechaCierre()))
                    .urlOficial(url)
                    .numeroConvocatoria(numConv)
                    .proyectoId(rec.getProyecto().getId())
                    .proyectoNombre(rec.getProyecto().getNombre())
                    .guia(guiaDto)
                    .creadoEn(rec.getGeneradaEn())
                    .puntuacion(rec.getPuntuacion())
                    .build());
        }

        // 2. Guías de análisis individuales (flujo catálogo → analizar con IA)
        List<AnalisisConvocatoria> analisis = analisisConvocatoriaRepository
                .findGuiasEnriquecidasByUsuarioId(usuario.getId());
        for (AnalisisConvocatoria a : analisis) {
            GuiaSubvencionDTO guiaDto = openAiGuiaService.deserializarGuia(a.getGuiaEnriquecida());
            if (guiaDto == null) continue;

            String numConv = a.getConvocatoria().getNumeroConvocatoria();
            String url = (numConv != null && !numConv.isBlank())
                    ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                    : a.getConvocatoria().getUrlOficial();

            guias.add(GuiaUsuarioDTO.builder()
                    .id(a.getId() + ANALISIS_ID_OFFSET) // offset to avoid ID collision with recomendaciones
                    .origen("analisis")
                    .convocatoriaId(a.getConvocatoria().getId())
                    .titulo(a.getConvocatoria().getTitulo())
                    .organismo(a.getConvocatoria().getOrganismo())
                    .sector(a.getConvocatoria().getSector())
                    .ubicacion(a.getConvocatoria().getUbicacion())
                    .fechaCierre(a.getConvocatoria().getFechaCierre())
                    .abierto(calcularAbierto(a.getConvocatoria().getAbierto(), a.getConvocatoria().getFechaCierre()))
                    .urlOficial(url)
                    .numeroConvocatoria(numConv)
                    .proyectoId(a.getProyecto() != null ? a.getProyecto().getId() : null)
                    .proyectoNombre(a.getProyecto() != null ? a.getProyecto().getNombre() : null)
                    .guia(guiaDto)
                    .creadoEn(a.getCreadoEn())
                    .puntuacion(0)
                    .build());
        }

        return ResponseEntity.ok(guias);
    }

    @Transactional
    @DeleteMapping("/{origen}/{id}")
    public ResponseEntity<?> eliminarGuia(@PathVariable String origen,
                                          @PathVariable Long id,
                                          Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);
        int eliminadas;

        switch (origen) {
            case "recomendacion" -> eliminadas = recomendacionRepository
                    .deleteByIdAndUsuarioId(id, usuario.getId());
            case "analisis" -> {
                long realId = id >= ANALISIS_ID_OFFSET ? id - ANALISIS_ID_OFFSET : id;
                eliminadas = analisisConvocatoriaRepository
                        .deleteByIdAndUsuarioId(realId, usuario.getId());
            }
            default -> {
                return ResponseEntity.badRequest().body("Origen inválido: " + origen);
            }
        }

        return eliminadas > 0 ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @Transactional(readOnly = true)
    @GetMapping("/{origen}/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String origen,
                                               @PathVariable Long id,
                                               @RequestParam(name = "lang", required = false) String lang,
                                               Authentication authentication) {
        if (!"recomendacion".equals(origen) && !"analisis".equals(origen)) {
            return ResponseEntity.badRequest().build();
        }

        String normalizedLang = GuiaTranslationService.normalizeLang(lang);
        Usuario usuario = resolverUsuario(authentication);
        GuiaUsuarioDTO guiaDto;

        switch (origen) {
            case "recomendacion" -> {
                Recomendacion rec = recomendacionRepository
                        .findByIdAndUsuarioIdConGuia(id, usuario.getId()).orElse(null);
                if (rec == null) return ResponseEntity.notFound().build();
                GuiaSubvencionDTO guiaSubv = openAiGuiaService.deserializarGuia(rec.getGuiaEnriquecida());
                if (guiaSubv == null) return ResponseEntity.notFound().build();
                guiaDto = buildDtoFromRecomendacion(rec, guiaSubv);
            }
            case "analisis" -> {
                long realId = id >= ANALISIS_ID_OFFSET ? id - ANALISIS_ID_OFFSET : id;
                AnalisisConvocatoria a = analisisConvocatoriaRepository
                        .findByIdAndUsuarioIdConGuia(realId, usuario.getId()).orElse(null);
                if (a == null) return ResponseEntity.notFound().build();
                GuiaSubvencionDTO guiaSubv = openAiGuiaService.deserializarGuia(a.getGuiaEnriquecida());
                if (guiaSubv == null) return ResponseEntity.notFound().build();
                guiaDto = buildDtoFromAnalisis(a, guiaSubv);
            }
            default -> { return ResponseEntity.badRequest().build(); }
        }

        guiaDto = guiaTranslationService.translate(guiaDto, normalizedLang);
        byte[] pdfBytes = guiaPdfService.generarPdf(guiaDto, normalizedLang);
        String filename = guiaPdfService.sanitizeFilename(guiaDto.getTitulo());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    private GuiaUsuarioDTO buildDtoFromRecomendacion(Recomendacion rec, GuiaSubvencionDTO guia) {
        String numConv = rec.getConvocatoria().getNumeroConvocatoria();
        String url = (numConv != null && !numConv.isBlank())
                ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                : rec.getConvocatoria().getUrlOficial();

        return GuiaUsuarioDTO.builder()
                .id(rec.getId())
                .origen("recomendacion")
                .titulo(rec.getConvocatoria().getTitulo())
                .organismo(rec.getConvocatoria().getOrganismo())
                .sector(rec.getConvocatoria().getSector())
                .ubicacion(rec.getConvocatoria().getUbicacion())
                .fechaCierre(rec.getConvocatoria().getFechaCierre())
                .urlOficial(url)
                .proyectoNombre(rec.getProyecto().getNombre())
                .guia(guia)
                .creadoEn(rec.getGeneradaEn())
                .puntuacion(rec.getPuntuacion())
                .build();
    }

    private GuiaUsuarioDTO buildDtoFromAnalisis(AnalisisConvocatoria a, GuiaSubvencionDTO guia) {
        String numConv = a.getConvocatoria().getNumeroConvocatoria();
        String url = (numConv != null && !numConv.isBlank())
                ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                : a.getConvocatoria().getUrlOficial();

        return GuiaUsuarioDTO.builder()
                .id(a.getId() + ANALISIS_ID_OFFSET)
                .origen("analisis")
                .titulo(a.getConvocatoria().getTitulo())
                .organismo(a.getConvocatoria().getOrganismo())
                .sector(a.getConvocatoria().getSector())
                .ubicacion(a.getConvocatoria().getUbicacion())
                .fechaCierre(a.getConvocatoria().getFechaCierre())
                .urlOficial(url)
                .proyectoNombre(a.getProyecto() != null ? a.getProyecto().getNombre() : null)
                .guia(guia)
                .creadoEn(a.getCreadoEn())
                .puntuacion(0)
                .build();
    }

    private Usuario resolverUsuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    private Boolean calcularAbierto(Boolean abierto, java.time.LocalDate fechaCierre) {
        if (Boolean.TRUE.equals(abierto)) return true;
        if (fechaCierre == null) return true;
        return !fechaCierre.isBefore(java.time.LocalDate.now());
    }
}
