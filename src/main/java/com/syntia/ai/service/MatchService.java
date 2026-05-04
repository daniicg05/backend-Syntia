package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.ConvocatoriaPublicaDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de cálculo de afinidad entre el contexto de un usuario
 * (perfil + proyectos) y una convocatoria.
 *
 * Algoritmo de scoring (total máximo = 100):
 *   - Sector del perfil vs convocatoria  → 0-40 pts
 *   - Sector de proyectos vs convocatoria → 0-25 pts (8 pts/proyecto, máx 25)
 *   - Ubicación                          → 0-15 pts
 *   - Overlap de keywords descripción    → 0-20 pts
 */
@Service
public class MatchService {

    // Keywords por sector (en minúsculas)
    private static final Map<String, List<String>> KEYWORDS_POR_SECTOR = Map.ofEntries(
            Map.entry("tecnologia",    List.of("tecnolog", "innov", "digital", "software", "informát", "computac", "datos", "inteligencia", "ia ", " ia,", "robót", "ciberseg", "telecomunic")),
            Map.entry("agricola",      List.of("agr", "ganad", "forestal", "pesca", "rural", "alimentar", "fitosanit", "acuicultura", "silvicultura")),
            Map.entry("industrial",    List.of("industri", "manufactur", "fabric", "producción", "metalurg", "siderurg", "automoción", "logístic", "cadena de suministro")),
            Map.entry("hosteleria",    List.of("hostel", "turis", "restaur", "hotel", "alojam", "gastrono", "ocio", "viaje")),
            Map.entry("social",        List.of("social", "cultur", "ong", "asociac", "cooperat", "fundac", "voluntar", "integrac", "igualdad", "inclusión")),
            Map.entry("medioambiente", List.of("ambient", "ecolog", "sostenib", "renovable", "verde", "residuo", "contamin", "biodiversid", "cambio climático", "eficiencia energética")),
            Map.entry("comercio",      List.of("comerc", "distribuc", "retail", "pyme", "autónom", "emprend", "startup", "microempres")),
            Map.entry("salud",         List.of("salud", "sanid", "médic", "farmacéut", "biotecno", "clínic", "hospital", "enfermedad")),
            Map.entry("educacion",     List.of("educ", "formac", "enseñanz", "universi", "escuel", "docent", "aprendiz", "cualificac"))
    );

    private static final Set<String> STOP_WORDS_ES = Set.of(
            "para", "como", "con", "por", "que", "una", "uno", "los", "las",
            "del", "este", "esta", "estos", "estas", "ser", "tiene", "entre",
            "más", "sobre", "desde", "hasta", "cuando", "donde", "cuyo", "cuya",
            "todos", "toda", "todas", "cada", "otros", "otras", "otro", "otra"
    );

    /**
     * Calcula la puntuación de afinidad entre el contexto del usuario y una convocatoria.
     *
     * @param perfil    perfil del usuario (puede ser null si no está completado)
     * @param proyectos lista de proyectos del usuario (puede estar vacía)
     * @param conv      convocatoria a evaluar
     * @return puntuación 0-100 y razón del match
     */
    public ResultadoMatch calcular(Perfil perfil, List<Proyecto> proyectos, Convocatoria conv) {
        int score = 0;
        List<String> razones = new ArrayList<>();

        String textoConv = buildTextoConvocatoria(conv);

        // ── 1. Sector del perfil ────────────────────────────────────────────────
        if (perfil != null && perfil.getSector() != null) {
            int sectorScore = calcularSectorScore(perfil.getSector(), textoConv);
            if (sectorScore > 0) {
                score += Math.min(sectorScore, 40);
                razones.add("Tu sector: " + perfil.getSector());
            }
        }

        // ── 2. Sectores de proyectos ────────────────────────────────────────────
        if (proyectos != null && !proyectos.isEmpty()) {
            int proyectoScore = 0;
            List<String> proyectosMatch = new ArrayList<>();
            for (Proyecto p : proyectos) {
                if (p.getSector() == null) continue;
                int ps = calcularSectorScore(p.getSector(), textoConv);
                if (ps > 0) {
                    proyectoScore += 8;
                    proyectosMatch.add(p.getNombre());
                }
            }
            int capped = Math.min(proyectoScore, 25);
            score += capped;
            if (!proyectosMatch.isEmpty()) {
                razones.add("Proyectos: " + String.join(", ", proyectosMatch.stream().limit(2).toList()));
            }
        }

        // ── 3. Ubicación ────────────────────────────────────────────────────────
        String ubicConv = nullSafe(conv.getUbicacion()).toLowerCase();
        if (ubicConv.contains("nacional") || ubicConv.isBlank()) {
            score += 15; // Convocatorias nacionales son siempre relevantes
        } else if (perfil != null && perfil.getUbicacion() != null) {
            String ubicUser = perfil.getUbicacion().toLowerCase();
            if (ubicConv.contains(ubicUser.substring(0, Math.min(5, ubicUser.length()))) ||
                    ubicUser.contains(ubicConv.substring(0, Math.min(5, ubicConv.length())))) {
                score += 15;
                razones.add("Misma ubicación");
            }
        }

        // ── 4. Overlap de keywords en descripciones ─────────────────────────────
        String userText = buildUserText(perfil, proyectos);
        if (!userText.isBlank()) {
            int overlapScore = calcularOverlapScore(userText, textoConv);
            score += overlapScore;
        }

        score = Math.min(score, 100);
        String razon = razones.isEmpty() ? "Convocatoria reciente" : String.join(" · ", razones);
        return new ResultadoMatch(score, razon);
    }

    /**
     * Construye el DTO de convocatoria con score de match incluido.
     */
    public ConvocatoriaPublicaDTO toMatchDTO(Convocatoria c, Perfil perfil, List<Proyecto> proyectos) {
        ResultadoMatch resultado = calcular(perfil, proyectos, c);
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
                .urlOficial(construirUrl(c))
                .idBdns(c.getIdBdns())
                .numeroConvocatoria(c.getNumeroConvocatoria())
                .matchScore(resultado.score())
                .matchRazon(resultado.razon())
                .presupuesto(c.getPresupuesto())
                .regionId(c.getRegionId())
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private int calcularSectorScore(String sector, String textoConv) {
        if (sector == null) return 0;
        List<String> keywords = KEYWORDS_POR_SECTOR.getOrDefault(sector.toLowerCase(), List.of());
        if (keywords.isEmpty()) {
            // Sector no mapeado: coincidencia literal
            return textoConv.contains(sector.toLowerCase()) ? 25 : 0;
        }
        long matches = keywords.stream().filter(textoConv::contains).count();
        return matches > 0 ? 40 : 0;
    }

    private int calcularOverlapScore(String userText, String convText) {
        Set<String> userWords = tokenizar(userText);
        Set<String> convWords = tokenizar(convText);
        long comunes = userWords.stream().filter(convWords::contains).count();
        return (int) Math.min(comunes * 3, 20);
    }

    private Set<String> tokenizar(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase().split("[^a-záéíóúüñ]+"))
                .filter(w -> w.length() > 4)
                .filter(w -> !STOP_WORDS_ES.contains(w))
                .collect(Collectors.toSet());
    }

    private String buildTextoConvocatoria(Convocatoria c) {
        String descripcion = c.getDescripcion() != null
                ? c.getDescripcion().substring(0, Math.min(c.getDescripcion().length(), 500))
                : "";
        return (nullSafe(c.getSector()) + " " + nullSafe(c.getTitulo()) + " " + descripcion).toLowerCase();
    }

    private String buildUserText(Perfil perfil, List<Proyecto> proyectos) {
        StringBuilder sb = new StringBuilder();
        if (perfil != null) {
            sb.append(nullSafe(perfil.getSector())).append(" ");
            sb.append(nullSafe(perfil.getDescripcionLibre())).append(" ");
            sb.append(nullSafe(perfil.getObjetivos())).append(" ");
        }
        if (proyectos != null) {
            for (Proyecto p : proyectos) {
                sb.append(nullSafe(p.getNombre())).append(" ");
                sb.append(nullSafe(p.getSector())).append(" ");
                sb.append(nullSafe(p.getDescripcion())).append(" ");
            }
        }
        return sb.toString().toLowerCase();
    }

    private String construirUrl(Convocatoria c) {
        if (c.getNumeroConvocatoria() != null && !c.getNumeroConvocatoria().isBlank()) {
            return "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias?numConv=" + c.getNumeroConvocatoria();
        }
        if (c.getIdBdns() != null && !c.getIdBdns().isBlank()) {
            return "https://www.infosubvenciones.es/bdnstrans/GE/es/convocatorias/" + c.getIdBdns();
        }
        String url = c.getUrlOficial();
        if (url != null) url = url.replace("/bdnstrans/GE/es/convocatoria/", "/bdnstrans/GE/es/convocatorias/");
        return url;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private Boolean calcularAbierto(Boolean abierto, java.time.LocalDate fechaCierre) {
        if (Boolean.TRUE.equals(abierto)) return true;
        if (fechaCierre == null) return true;
        return !fechaCierre.isBefore(java.time.LocalDate.now());
    }

    /** Resultado del cálculo de match: score + razón legible. */
    public record ResultadoMatch(int score, String razon) {}
}
