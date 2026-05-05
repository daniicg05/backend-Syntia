package com.syntia.ai.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntia.ai.model.AnalisisConvocatoria;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.AnalisisCompletoDTO;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.repository.AnalisisConvocatoriaRepository;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.repository.IdxConvocatoriaBeneficiarioRepository;
import com.syntia.ai.service.BdnsClientService;
import com.syntia.ai.service.ConvocatoriaContextBuilder;
import com.syntia.ai.service.MatchService;
import com.syntia.ai.service.OpenAiAnalisisService;
import com.syntia.ai.service.OpenAiGuiaService;
import com.syntia.ai.service.OpenAiMatchingService;
import com.syntia.ai.service.PerfilService;
import com.syntia.ai.service.ProyectoService;
import com.syntia.ai.service.RegionService;
import com.syntia.ai.service.UbicacionNormalizador;
import com.syntia.ai.service.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Endpoints autenticados de búsqueda y recomendaciones personalizadas.
 * Combina el matching de perfil+proyectos con las convocatorias de la BD.
 */
@Slf4j
@RestController
@RequestMapping("/api/usuario/convocatorias")
public class ConvocatoriaPersonalizadaController {

    private static final int POOL_SECTOR = 80;   // candidatos filtrados por sector
    private static final int POOL_RECIENTES = 40; // candidatos recientes de fallback
    private static final Map<String, List<String>> KEYWORDS_SECTOR = Map.ofEntries(
            Map.entry("tecnologia",    List.of("tecnolog", "innov", "digital")),
            Map.entry("agricola",      List.of("agr", "ganad", "rural")),
            Map.entry("industrial",    List.of("industri", "manufactur", "producción")),
            Map.entry("hosteleria",    List.of("hostel", "turis", "restaur")),
            Map.entry("social",        List.of("social", "cultur", "asociac")),
            Map.entry("medioambiente", List.of("ambient", "sostenib", "renovable")),
            Map.entry("comercio",      List.of("comerc", "pyme", "emprend")),
            Map.entry("salud",         List.of("salud", "médic", "sanid")),
            Map.entry("educacion",     List.of("educ", "formac", "universi"))
    );

    private final UsuarioService usuarioService;
    private final PerfilService perfilService;
    private final ProyectoService proyectoService;
    private final ConvocatoriaRepository convocatoriaRepository;
    private final IdxConvocatoriaBeneficiarioRepository beneficiarioRepository;
    private final AnalisisConvocatoriaRepository analisisConvocatoriaRepository;
    private final MatchService matchService;
    private final RegionService regionService;
    private final OpenAiGuiaService openAiGuiaService;
    private final OpenAiMatchingService openAiMatchingService;
    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaContextBuilder contextBuilder;
    private final OpenAiAnalisisService openAiAnalisisService;
    private final ObjectMapper objectMapper;

    public ConvocatoriaPersonalizadaController(UsuarioService usuarioService,
                                               PerfilService perfilService,
                                               ProyectoService proyectoService,
                                               ConvocatoriaRepository convocatoriaRepository,
                                               IdxConvocatoriaBeneficiarioRepository beneficiarioRepository,
                                               AnalisisConvocatoriaRepository analisisConvocatoriaRepository,
                                               MatchService matchService,
                                               RegionService regionService,
                                               OpenAiGuiaService openAiGuiaService,
                                               OpenAiMatchingService openAiMatchingService,
                                               BdnsClientService bdnsClientService,
                                               ConvocatoriaContextBuilder contextBuilder,
                                               OpenAiAnalisisService openAiAnalisisService) {
        this.usuarioService = usuarioService;
        this.perfilService = perfilService;
        this.proyectoService = proyectoService;
        this.convocatoriaRepository = convocatoriaRepository;
        this.beneficiarioRepository = beneficiarioRepository;
        this.analisisConvocatoriaRepository = analisisConvocatoriaRepository;
        this.matchService = matchService;
        this.regionService = regionService;
        this.openAiGuiaService = openAiGuiaService;
        this.openAiMatchingService = openAiMatchingService;
        this.bdnsClientService = bdnsClientService;
        this.contextBuilder = contextBuilder;
        this.openAiAnalisisService = openAiAnalisisService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Recomendaciones personalizadas para el Home.
     * Devuelve convocatorias ordenadas por score de afinidad.
     * ?page=0&size=16
     */
    @GetMapping("/recomendadas")
    public ResponseEntity<?> recomendadas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "16") int size,
            Authentication authentication) {

        Usuario usuario = resolverUsuario(authentication);
        Optional<Perfil> perfilOpt = perfilService.obtenerPerfil(usuario.getId());

        Perfil perfil = perfilOpt.orElse(null);
        List<Convocatoria> pool = buildPool(perfil);

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        int from = safePage * safeSize;

        List<ConvocatoriaPublicaDTO> todas = pool.stream()
                .map(c -> matchService.toMatchDTO(c, perfil, List.of()))
                .sorted(Comparator.comparingInt(ConvocatoriaPublicaDTO::getMatchScore).reversed())
                .toList();

        List<ConvocatoriaPublicaDTO> pagina = todas.stream()
                .skip(from)
                .limit(safeSize)
                .toList();

        long total = todas.size();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) total / safeSize) : 0;

        return ResponseEntity.ok(Map.of(
                "content", pagina,
                "totalElements", total,
                "totalPages", totalPages,
                "page", safePage,
                "size", safeSize
        ));
    }

    /**
     * Búsqueda autenticada con match score.
     * ?q=&sector=&page=0&size=20
     */
    /**
     * Búsqueda autenticada con match score.
     * @param sort criterio de orden: "relevancia" (por matchScore), "plazo" (fechaCierre ASC), "cuantia" (presupuesto DESC)
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
            @RequestParam(defaultValue = "relevancia") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Usuario usuario = resolverUsuario(authentication);
        Optional<Perfil> perfilOpt = perfilService.obtenerPerfil(usuario.getId());
        List<Proyecto> proyectos = proyectoService.obtenerProyectos(usuario.getId());
        Perfil perfil = perfilOpt.orElse(null);

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);

        // Fallback temporal para compatibilidad con el front estándar que aún envía ubicación en texto
        if (regionId == null && ubicacion != null && !ubicacion.isBlank()) {
            Integer mappedId = UbicacionNormalizador.normalizarARegionId(ubicacion);
            if (mappedId != null) {
                regionId = mappedId.longValue();
            }
        }

        boolean filtrarRegion = regionId != null;
        Set<Integer> regionIds = filtrarRegion
                ? regionService.obtenerDescendientesIds(regionId)
                : Set.of(-1);
        boolean filtrarFechaCierre = plazoCierreDias != null && plazoCierreDias > 0;
        LocalDate fechaCierreHasta = filtrarFechaCierre
                ? LocalDate.now().plusDays(plazoCierreDias)
                : LocalDate.of(2099, 12, 31);
        boolean filtrarPresupuesto = presupuestoMin != null && presupuestoMin > 0;
        Double presupuestoMinEfectivo = filtrarPresupuesto ? presupuestoMin : 0.0;

        // Pool grande para re-ordenar por score/criterio
        PageRequest pageRequest = PageRequest.of(0, 200);
        var candidatos = convocatoriaRepository.buscarPublicoConRegion(
                q.isBlank() ? null : q,
                sector.isBlank() ? null : sector,
                tipo.isBlank() ? null : tipo,
                abierto == null || !abierto,
                filtrarRegion,
                regionIds,
                filtrarPresupuesto,
                presupuestoMinEfectivo,
                filtrarFechaCierre,
                fechaCierreHasta,
                tipoBeneficiario.isBlank() ? null : tipoBeneficiario,
                pageRequest
        );

        Map<String, List<String>> beneficiarioMap = cargarBeneficiarios(candidatos.getContent());

        // Calcular matchScore para todos los candidatos
        List<ConvocatoriaPublicaDTO> scorados = candidatos.getContent().stream()
                .map(c -> matchService.toMatchDTO(c, perfil, proyectos))
                .peek(dto -> dto.setTiposBeneficiario(
                        dto.getNumeroConvocatoria() != null
                                ? beneficiarioMap.getOrDefault(dto.getNumeroConvocatoria(), List.of())
                                : List.of()))
                .toList();

        // Ordenar según criterio solicitado
        Comparator<ConvocatoriaPublicaDTO> comparator = switch (sort) {
            case "plazo" -> Comparator.comparing(
                    (ConvocatoriaPublicaDTO d) -> d.getFechaCierre() != null ? d.getFechaCierre() : java.time.LocalDate.MAX);
            case "cuantia" -> Comparator.comparing(
                    (ConvocatoriaPublicaDTO d) -> d.getPresupuesto() != null ? d.getPresupuesto() : 0.0,
                    Comparator.reverseOrder());
            default -> Comparator.comparingInt(ConvocatoriaPublicaDTO::getMatchScore).reversed();
        };
        List<ConvocatoriaPublicaDTO> ordenados = scorados.stream().sorted(comparator).toList();

        int from = safePage * safeSize;
        List<ConvocatoriaPublicaDTO> pagina = ordenados.stream()
                .skip(from)
                .limit(safeSize)
                .toList();

        long total = candidatos.getTotalElements();
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return ResponseEntity.ok(Map.of(
                "content", pagina,
                "totalElements", total,
                "totalPages", totalPages,
                "page", safePage,
                "size", safeSize
        ));
    }

    /**
     * Análisis completo de una convocatoria con IA.
     * Recopila datos de BD local + catálogos + BDNS live + perfil + proyecto en paralelo,
     * y genera un informe estructurado en slides para la galería interactiva.
     *
     * @param id         ID de la convocatoria
     * @param proyectoId ID del proyecto del usuario (opcional; si no se pasa, usa el más afín)
     */
    private Map<String, List<String>> cargarBeneficiarios(List<Convocatoria> convocatorias) {
        Set<String> numeros = new LinkedHashSet<>();
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

    @Transactional
    @GetMapping("/{id}/analisis")
    public ResponseEntity<?> analisis(@PathVariable Long id,
                                      @RequestParam(required = false) Long proyectoId,
                                      Authentication authentication) {
      try {
        Usuario usuario = resolverUsuario(authentication);

        Convocatoria convocatoria = convocatoriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Convocatoria no encontrada: " + id));

        // Check cache
        Optional<AnalisisConvocatoria> cached = analisisConvocatoriaRepository
                .findByConvocatoriaIdAndUsuarioId(id, usuario.getId());
        if (cached.isPresent()) {
            try {
                AnalisisCompletoDTO dto = objectMapper.readValue(cached.get().getResultado(), AnalisisCompletoDTO.class);
                log.debug("Análisis cacheado para convocatoria={} usuario={}", id, usuario.getId());
                return ResponseEntity.ok(dto);
            } catch (Exception e) {
                log.warn("Error deserializando análisis cacheado, regenerando: {}", e.getMessage());
            }
        }

        // Load user data
        Perfil perfil = perfilService.obtenerPerfil(usuario.getId()).orElse(null);
        Proyecto proyecto = resolverProyecto(usuario, proyectoId, convocatoria);

        // Build comprehensive context (parallel: BD + catalogs + BDNS live)
        String contexto = contextBuilder.buildContext(convocatoria, perfil, proyecto);

        // Single AI call with all context
        AnalisisCompletoDTO analisis = openAiAnalisisService.analizar(contexto);

        // Generate enriched guide in the same flow (cheaper than a separate call)
        String guiaJson = null;
        try {
            String numConv = convocatoria.getNumeroConvocatoria();
            String detalleTexto = (numConv != null) ? bdnsClientService.obtenerDetalleTexto(numConv) : null;
            String urlOficial = (numConv != null && !numConv.isBlank())
                    ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                    : convocatoria.getUrlOficial();

            GuiaSubvencionDTO guia = (proyecto != null)
                    ? openAiGuiaService.generarGuia(proyecto, perfil, convocatoria, detalleTexto, urlOficial)
                    : openAiGuiaService.generarGuiaSinProyecto(perfil, convocatoria, detalleTexto, urlOficial);
            guiaJson = openAiGuiaService.serializarGuia(guia);
        } catch (Exception e) {
            log.warn("Error generando guía enriquecida para convocatoria={}: {}", id, e.getMessage());
        }

        // Persist analysis + guide
        try {
            String json = objectMapper.writeValueAsString(analisis);
            AnalisisConvocatoria entity = cached.orElseGet(() ->
                    AnalisisConvocatoria.builder()
                            .convocatoria(convocatoria)
                            .usuario(usuario)
                            .proyecto(proyecto)
                            .build());
            entity.setResultado(json);
            entity.setProyecto(proyecto);
            entity.setGuiaEnriquecida(guiaJson);
            analisisConvocatoriaRepository.save(entity);
            log.info("Análisis y guía guardados para convocatoria={} usuario={}", id, usuario.getId());
        } catch (Exception e) {
            log.warn("Error persistiendo análisis: {}", e.getMessage());
        }

        return ResponseEntity.ok(analisis);
      } catch (EntityNotFoundException e) {
          return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
      } catch (Exception e) {
          log.error("Error en análisis convocatoria={}: {}", id, e.getMessage(), e);
          return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error interno del servidor"));
      }
    }

    /**
     * Devuelve la guía enriquecida de una convocatoria previamente analizada.
     * Si no existe aún (análisis antiguo sin guía), la genera bajo demanda.
     */
    @Transactional
    @GetMapping("/{id}/guia")
    public ResponseEntity<?> guia(@PathVariable Long id, Authentication authentication) {
        Usuario usuario = resolverUsuario(authentication);

        Optional<AnalisisConvocatoria> cached = analisisConvocatoriaRepository
                .findByConvocatoriaIdAndUsuarioId(id, usuario.getId());

        if (cached.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Primero analiza esta convocatoria con IA."));
        }

        AnalisisConvocatoria ac = cached.get();

        // If guide already exists, return it
        if (ac.getGuiaEnriquecida() != null && !ac.getGuiaEnriquecida().isBlank()) {
            GuiaSubvencionDTO guia = openAiGuiaService.deserializarGuia(ac.getGuiaEnriquecida());
            if (guia != null) {
                return ResponseEntity.ok(guia);
            }
        }

        // Generate guide on demand
        try {
            Convocatoria convocatoria = ac.getConvocatoria();
            Perfil perfil = perfilService.obtenerPerfil(usuario.getId()).orElse(null);
            Proyecto proyecto = ac.getProyecto();

            String numConv = convocatoria.getNumeroConvocatoria();
            String detalleTexto = (numConv != null) ? bdnsClientService.obtenerDetalleTexto(numConv) : null;
            String urlOficial = (numConv != null && !numConv.isBlank())
                    ? "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatoria/" + numConv
                    : convocatoria.getUrlOficial();

            GuiaSubvencionDTO guia = (proyecto != null)
                    ? openAiGuiaService.generarGuia(proyecto, perfil, convocatoria, detalleTexto, urlOficial)
                    : openAiGuiaService.generarGuiaSinProyecto(perfil, convocatoria, detalleTexto, urlOficial);

            ac.setGuiaEnriquecida(openAiGuiaService.serializarGuia(guia));
            analisisConvocatoriaRepository.save(ac);
            log.info("Guía generada bajo demanda para convocatoria={} usuario={}", id, usuario.getId());

            return ResponseEntity.ok(guia);
        } catch (Exception e) {
            log.error("Error generando guía bajo demanda para convocatoria={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Error generando la guía: " + e.getMessage()));
        }
    }

    /**
     * Resuelve el proyecto a usar para el análisis.
     * Si se pasa proyectoId, lo usa. Si no, busca el más afín por sector.
     */
    private Proyecto resolverProyecto(Usuario usuario, Long proyectoId, Convocatoria convocatoria) {
        List<Proyecto> proyectos = proyectoService.obtenerProyectos(usuario.getId());
        if (proyectos.isEmpty()) return null;

        if (proyectoId != null) {
            return proyectos.stream()
                    .filter(p -> p.getId().equals(proyectoId))
                    .findFirst()
                    .orElse(proyectos.get(0));
        }

        // Pick project with matching sector, or first
        String sectorConv = convocatoria.getSector();
        if (sectorConv != null && !sectorConv.isBlank()) {
            String sectorLower = sectorConv.toLowerCase();
            return proyectos.stream()
                    .filter(p -> p.getSector() != null && p.getSector().toLowerCase().contains(sectorLower))
                    .findFirst()
                    .orElse(proyectos.get(0));
        }
        return proyectos.get(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Construye el pool de candidatos: convocatorias filtradas por sector del usuario
     * más un conjunto de recientes para garantizar variedad.
     */
    private List<Convocatoria> buildPool(Perfil perfil) {
        LinkedHashSet<Convocatoria> pool = new LinkedHashSet<>();

        // Sector del perfil (únicamente)
        if (perfil != null && perfil.getSector() != null) {
            List<String> kws = getKeywords(perfil.getSector());
            pool.addAll(convocatoriaRepository.buscarCandidatosPorKeywords(
                    kws.size() > 0 ? kws.get(0) : "",
                    kws.size() > 1 ? kws.get(1) : "",
                    kws.size() > 2 ? kws.get(2) : "",
                    PageRequest.of(0, POOL_SECTOR)
            ));
        }

        // Recientes de fallback
        pool.addAll(convocatoriaRepository.findTop16ByOrderByIdDesc());

        return new ArrayList<>(pool);
    }

    private List<String> getKeywords(String sector) {
        return KEYWORDS_SECTOR.getOrDefault(sector.toLowerCase(), List.of(sector.toLowerCase()));
    }

    private Usuario resolverUsuario(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UsernameNotFoundException("Usuario no autenticado");
        }
        return usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
