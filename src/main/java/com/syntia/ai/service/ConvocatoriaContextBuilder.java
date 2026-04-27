package com.syntia.ai.service;

import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConvocatoriaContextBuilder {

    private static final int MAX_DETALLE_CHARS = 12000;
    private static final int MAX_ANUNCIO_CHARS = 8000;

    private final BdnsClientService bdnsClientService;
    private final IdxConvocatoriaBeneficiarioRepository beneficiarioRepo;
    private final IdxConvocatoriaFinalidadRepository finalidadRepo;
    private final IdxConvocatoriaInstrumentoRepository instrumentoRepo;
    private final IdxConvocatoriaOrganoRepository organoRepo;
    private final IdxConvocatoriaRegionRepository regionRepo;
    private final IdxConvocatoriaTipoAdminRepository tipoAdminRepo;
    private final IdxConvocatoriaActividadRepository actividadRepo;
    private final IdxConvocatoriaReglamentoRepository reglamentoRepo;
    private final IdxConvocatoriaObjetivoRepository objetivoRepo;
    private final IdxConvocatoriaSectorProductoRepository sectorProductoRepo;

    /**
     * Builds a comprehensive text context for the AI prompt by gathering data
     * from all available sources in parallel.
     */
    public String buildContext(Convocatoria convocatoria, Perfil perfil,
                                Proyecto proyecto) {
        String numConv = convocatoria.getNumeroConvocatoria();
        boolean hasNum = numConv != null && !numConv.isBlank();

        // Launch parallel fetches
        CompletableFuture<String> detalleFuture = CompletableFuture.supplyAsync(() ->
                hasNum ? bdnsClientService.obtenerDetalleTexto(numConv) : null
        );
        CompletableFuture<Map<String, Object>> liveFuture = CompletableFuture.supplyAsync(() ->
                hasNum ? bdnsClientService.obtenerDetalleLive(numConv) : null
        );
        CompletableFuture<CatalogData> catalogFuture = CompletableFuture.supplyAsync(() ->
                hasNum ? loadCatalogData(numConv) : CatalogData.EMPTY
        );

        // Wait for all (max 15s)
        String detalleTexto;
        Map<String, Object> liveData;
        CatalogData catalog;
        try {
            CompletableFuture.allOf(detalleFuture, liveFuture, catalogFuture)
                    .get(15, TimeUnit.SECONDS);
            detalleTexto = detalleFuture.get();
            liveData = liveFuture.get();
            catalog = catalogFuture.get();
        } catch (Exception e) {
            log.warn("Error fetching context in parallel, using fallbacks: {}", e.getMessage());
            detalleTexto = detalleFuture.getNow(null);
            liveData = liveFuture.getNow(null);
            catalog = catalogFuture.getNow(CatalogData.EMPTY);
        }

        return buildPromptText(convocatoria, detalleTexto, liveData, catalog, perfil, proyecto);
    }

    private String buildPromptText(Convocatoria c, String detalleTexto,
                                    Map<String, Object> live, CatalogData catalog,
                                    Perfil perfil, Proyecto proyecto) {
        StringBuilder sb = new StringBuilder();

        // ── CONVOCATORIA BASIC DATA ──
        sb.append("=== CONVOCATORIA ===\n");
        sb.append("Titulo: ").append(c.getTitulo()).append("\n");
        appendIfPresent(sb, "Organismo", c.getOrganismo());
        appendIfPresent(sb, "Tipo", c.getTipo());
        appendIfPresent(sb, "Ambito geografico", c.getUbicacion());
        appendIfPresent(sb, "Sector", c.getSector());
        appendIfPresent(sb, "Finalidad", c.getFinalidad());
        if (c.getPresupuesto() != null)
            sb.append("Presupuesto total: ").append(String.format("%.2f", c.getPresupuesto())).append(" EUR\n");
        if (c.getAbierto() != null)
            sb.append("Estado: ").append(c.getAbierto() ? "ABIERTA" : "CERRADA").append("\n");
        if (c.getMrr() != null && c.getMrr())
            sb.append("Fondo MRR: Si\n");

        // ── DATES ──
        sb.append("\n=== PLAZOS ===\n");
        if (c.getFechaPublicacion() != null) sb.append("Publicacion: ").append(c.getFechaPublicacion()).append("\n");
        if (c.getFechaInicio() != null) sb.append("Inicio solicitudes: ").append(c.getFechaInicio()).append("\n");
        if (c.getFechaCierre() != null) sb.append("Cierre: ").append(c.getFechaCierre()).append("\n");

        // ── LIVE BDNS DATA ──
        if (live != null) {
            appendLiveData(sb, live);
        }

        // ── CATALOG DATA ──
        if (catalog != CatalogData.EMPTY) {
            sb.append("\n=== CLASIFICACION BDNS ===\n");
            appendList(sb, "Beneficiarios admitidos", catalog.beneficiarios);
            appendList(sb, "Finalidades", catalog.finalidades);
            appendList(sb, "Instrumentos", catalog.instrumentos);
            appendList(sb, "Actividades economicas", catalog.actividades);
            appendList(sb, "Reglamentos aplicables", catalog.reglamentos);
            appendList(sb, "Objetivos", catalog.objetivos);
            appendList(sb, "Sectores producto", catalog.sectoresProducto);
            appendList(sb, "Organos", catalog.organos);
            appendList(sb, "Regiones", catalog.regiones);
            appendList(sb, "Tipo administracion", catalog.tipoAdmin);
        }

        // ── DETAIL TEXT (the most important content for the AI) ──
        if (detalleTexto != null && !detalleTexto.isBlank()) {
            sb.append("\n=== CONTENIDO OFICIAL DE LA CONVOCATORIA ===\n");
            String limpio = cleanText(detalleTexto, MAX_DETALLE_CHARS);
            sb.append(limpio).append("\n");
            sb.append("(FUENTE PRIMARIA: usa este contenido para requisitos, documentacion y procedimiento exactos.)\n");
        }

        // ── FULL TEXT from DB if available ──
        if (c.getTextoCompleto() != null && !c.getTextoCompleto().isBlank()
                && (detalleTexto == null || detalleTexto.isBlank())) {
            sb.append("\n=== TEXTO COMPLETO (BD LOCAL) ===\n");
            sb.append(cleanText(c.getTextoCompleto(), MAX_DETALLE_CHARS)).append("\n");
        }

        // ── USER PROFILE ──
        if (perfil != null) {
            sb.append("\n=== PERFIL DEL SOLICITANTE ===\n");
            appendIfPresent(sb, "Tipo de entidad", perfil.getTipoEntidad());
            appendIfPresent(sb, "Empresa", perfil.getEmpresa());
            appendIfPresent(sb, "Sector", perfil.getSector());
            appendIfPresent(sb, "Ubicacion", perfil.getUbicacion());
            appendIfPresent(sb, "Provincia", perfil.getProvincia());
            appendIfPresent(sb, "Objetivos", perfil.getObjetivos());
            appendIfPresent(sb, "Necesidades financiacion", perfil.getNecesidadesFinanciacion());
            appendIfPresent(sb, "Descripcion", perfil.getDescripcionLibre());
        }

        // ── USER PROJECT ──
        if (proyecto != null) {
            sb.append("\n=== PROYECTO DEL SOLICITANTE ===\n");
            appendIfPresent(sb, "Nombre", proyecto.getNombre());
            appendIfPresent(sb, "Sector", proyecto.getSector());
            appendIfPresent(sb, "Ubicacion", proyecto.getUbicacion());
            appendIfPresent(sb, "Descripcion", proyecto.getDescripcion());
        }

        // ── INSTRUCTION ──
        sb.append("\n=== INSTRUCCION ===\n");
        sb.append("Genera el analisis completo en JSON siguiendo el esquema del system prompt.\n");
        if (detalleTexto != null && !detalleTexto.isBlank()) {
            sb.append("Usa el CONTENIDO OFICIAL como fuente primaria para requisitos y guia.\n");
        } else {
            sb.append("No hay contenido oficial disponible. Infiere del titulo, organismo y catalogos.\n");
        }
        if (perfil != null) {
            sb.append("PERSONALIZA la evaluacion de compatibilidad al perfil del solicitante.\n");
        } else {
            sb.append("No hay perfil. Usa nivel NO_EVALUABLE en compatibilidad.\n");
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendLiveData(StringBuilder sb, Map<String, Object> live) {
        sb.append("\n=== DATOS BDNS EN TIEMPO REAL ===\n");

        // Organo hierarchy
        if (live.get("organo") instanceof Map<?, ?> organo) {
            appendIfPresent(sb, "Organo nivel 1", toStr(organo.get("nivel1")));
            appendIfPresent(sb, "Organo nivel 2", toStr(organo.get("nivel2")));
            appendIfPresent(sb, "Organo nivel 3", toStr(organo.get("nivel3")));
        }

        appendIfPresent(sb, "Tipo convocatoria", toStr(live.get("tipoConvocatoria")));
        appendIfPresent(sb, "Bases reguladoras", toStr(live.get("descripcionBasesReguladoras")));
        appendIfPresent(sb, "URL bases reguladoras", toStr(live.get("urlBasesReguladoras")));
        appendIfPresent(sb, "Sede electronica", toStr(live.get("sedeElectronica")));
        appendIfPresent(sb, "Inicio solicitud", toStr(live.get("fechaInicioSolicitud")));
        appendIfPresent(sb, "Fin solicitud", toStr(live.get("fechaFinSolicitud")));
        appendIfPresent(sb, "Descripcion inicio", toStr(live.get("textInicio")));
        appendIfPresent(sb, "Descripcion fin", toStr(live.get("textFin")));
        appendIfPresent(sb, "Ayuda estado", toStr(live.get("ayudaEstado")));
        appendIfPresent(sb, "URL ayuda estado", toStr(live.get("urlAyudaEstado")));
        appendIfPresent(sb, "Reglamento", toStr(live.get("reglamento")));

        if (live.get("presupuestoTotal") instanceof Number n) {
            sb.append("Dotacion total BDNS: ").append(n).append(" EUR\n");
        }
        if (live.get("sePublicaDiarioOficial") instanceof Boolean b) {
            sb.append("Publicado en diario oficial: ").append(b ? "Si" : "No").append("\n");
        }

        // Fondos
        List<String> fondos = extractDescriptions(live.get("fondos"));
        if (!fondos.isEmpty()) sb.append("Fondos: ").append(String.join(", ", fondos)).append("\n");

        // Anuncios (full text from BOE, very important)
        if (live.get("anuncios") instanceof List<?> anList && !anList.isEmpty()) {
            Object first = anList.get(0);
            if (first instanceof Map<?, ?> am) {
                Object textoAnuncio = am.get("texto");
                if (textoAnuncio instanceof String s && !s.isBlank()) {
                    sb.append("\n=== TEXTO ANUNCIO OFICIAL (BOE/BOLETIN) ===\n");
                    sb.append(cleanText(s, MAX_ANUNCIO_CHARS)).append("\n");
                }
            }
        }

        // Documents listing
        if (live.get("documentos") instanceof List<?> docList && !docList.isEmpty()) {
            sb.append("\n=== DOCUMENTOS DISPONIBLES ===\n");
            for (Object d : docList) {
                if (d instanceof Map<?, ?> dm) {
                    String nombre = toStr(dm.get("nombreFic"));
                    String desc = toStr(dm.get("descripcion"));
                    if (nombre != null) {
                        sb.append("- ").append(nombre);
                        if (desc != null) sb.append(" (").append(desc).append(")");
                        sb.append("\n");
                    }
                }
            }
        }
    }

    private CatalogData loadCatalogData(String numConv) {
        try {
            List<String> beneficiarios = beneficiarioRepo.findBeneficiariosByNumeros(Set.of(numConv))
                    .stream().map(row -> (String) row[1]).toList();
            return new CatalogData(
                    beneficiarios,
                    finalidadRepo.findDescripcionesByNumeroConvocatoria(numConv),
                    instrumentoRepo.findDescripcionesByNumeroConvocatoria(numConv),
                    organoRepo.findDescripcionesByNumeroConvocatoria(numConv),
                    regionRepo.findDescripcionesByNumeroConvocatoria(numConv),
                    tipoAdminRepo.findTiposAdminByNumeroConvocatoria(numConv),
                    actividadRepo.findDescripcionesByNumeroConvocatoria(numConv),
                    reglamentoRepo.findDescripcionesByNumeroConvocatoria(numConv),
                    objetivoRepo.findDescripcionesByNumeroConvocatoria(numConv),
                    sectorProductoRepo.findDescripcionesByNumeroConvocatoria(numConv)
            );
        } catch (Exception e) {
            log.warn("Error loading catalog data for {}: {}", numConv, e.getMessage());
            return CatalogData.EMPTY;
        }
    }

    // ── Helpers ──

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private void appendList(StringBuilder sb, String label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            sb.append(label).append(": ").append(String.join(", ", values)).append("\n");
        }
    }

    private String cleanText(String text, int maxChars) {
        if (text == null) return "";
        String clean = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (clean.length() > maxChars) {
            clean = clean.substring(0, maxChars) + "...";
        }
        return clean;
    }

    private String toStr(Object obj) {
        return obj instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private List<String> extractDescriptions(Object raw) {
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

    record CatalogData(List<String> beneficiarios, List<String> finalidades,
                        List<String> instrumentos, List<String> organos,
                        List<String> regiones, List<String> tipoAdmin,
                        List<String> actividades, List<String> reglamentos,
                        List<String> objetivos, List<String> sectoresProducto) {
        static final CatalogData EMPTY = new CatalogData(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
