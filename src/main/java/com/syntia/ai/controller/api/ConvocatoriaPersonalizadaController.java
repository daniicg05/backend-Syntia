package com.syntia.ai.controller.api;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.AnalisisCompletoDTO;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Endpoints autenticados de búsqueda y recomendaciones personalizadas.
 * Combina el matching de perfil+proyectos con las convocatorias de la BD.
 */
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
    private final MatchService matchService;
    private final RegionService regionService;
    private final OpenAiGuiaService openAiGuiaService;
    private final OpenAiMatchingService openAiMatchingService;
    private final BdnsClientService bdnsClientService;
    private final ConvocatoriaContextBuilder contextBuilder;
    private final OpenAiAnalisisService openAiAnalisisService;

    public ConvocatoriaPersonalizadaController(UsuarioService usuarioService,
                                               PerfilService perfilService,
                                               ProyectoService proyectoService,
                                               ConvocatoriaRepository convocatoriaRepository,
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
        this.matchService = matchService;
        this.regionService = regionService;
        this.openAiGuiaService = openAiGuiaService;
        this.openAiMatchingService = openAiMatchingService;
        this.bdnsClientService = bdnsClientService;
        this.contextBuilder = contextBuilder;
        this.openAiAnalisisService = openAiAnalisisService;
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
        List<Proyecto> proyectos = proyectoService.obtenerProyectos(usuario.getId());

        Perfil perfil = perfilOpt.orElse(null);
        List<Convocatoria> pool = buildPool(perfil, proyectos);

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        int from = safePage * safeSize;

        List<ConvocatoriaPublicaDTO> todas = pool.stream()
                .map(c -> matchService.toMatchDTO(c, perfil, proyectos))
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
                : Set.of();

        // Pool grande para re-ordenar por score/criterio
        PageRequest pageRequest = PageRequest.of(0, 200);
        var candidatos = convocatoriaRepository.buscarPublicoConRegion(
                q.isBlank() ? null : q,
                sector.isBlank() ? null : sector,
                tipo.isBlank() ? null : tipo,
                abierto == null || !abierto,
                filtrarRegion,
                regionIds,
                presupuestoMin != null && presupuestoMin > 0 ? presupuestoMin : null,
                pageRequest
        );

        // Calcular matchScore para todos los candidatos
        List<ConvocatoriaPublicaDTO> scorados = candidatos.getContent().stream()
                .map(c -> matchService.toMatchDTO(c, perfil, proyectos))
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
    @GetMapping("/{id}/analisis")
    public ResponseEntity<?> analisis(@PathVariable Long id,
                                      @RequestParam(required = false) Long proyectoId,
                                      Authentication authentication) {

        Usuario usuario = resolverUsuario(authentication);

        Convocatoria convocatoria = convocatoriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Convocatoria no encontrada: " + id));

        // Load user data
        Perfil perfil = perfilService.obtenerPerfil(usuario.getId()).orElse(null);
        Proyecto proyecto = resolverProyecto(usuario, proyectoId, convocatoria);

        // Build comprehensive context (parallel: BD + catalogs + BDNS live)
        String contexto = contextBuilder.buildContext(convocatoria, perfil, proyecto);

        // Single AI call with all context
        AnalisisCompletoDTO analisis = openAiAnalisisService.analizar(contexto);

        return ResponseEntity.ok(analisis);
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
    private List<Convocatoria> buildPool(Perfil perfil, List<Proyecto> proyectos) {
        LinkedHashSet<Convocatoria> pool = new LinkedHashSet<>();

        // Sector del perfil
        if (perfil != null && perfil.getSector() != null) {
            List<String> kws = getKeywords(perfil.getSector());
            pool.addAll(convocatoriaRepository.buscarCandidatosPorKeywords(
                    kws.size() > 0 ? kws.get(0) : "",
                    kws.size() > 1 ? kws.get(1) : "",
                    kws.size() > 2 ? kws.get(2) : "",
                    PageRequest.of(0, POOL_SECTOR)
            ));
        }

        // Sectores de proyectos
        for (Proyecto p : proyectos) {
            if (p.getSector() == null || pool.size() >= 150) break;
            List<String> kws = getKeywords(p.getSector());
            pool.addAll(convocatoriaRepository.buscarCandidatosPorKeywords(
                    kws.size() > 0 ? kws.get(0) : "",
                    kws.size() > 1 ? kws.get(1) : "",
                    kws.size() > 2 ? kws.get(2) : "",
                    PageRequest.of(0, 40)
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
