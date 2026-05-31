package com.syntia.ai.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.GuiaUsuarioDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class GuiaPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MessageSource messageSource;

    public GuiaPdfService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public byte[] generarPdf(GuiaUsuarioDTO dto, String lang) {
        Locale locale = Locale.forLanguageTag(lang != null ? lang : "es");
        String html = buildHtml(dto, locale);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF para guia id={}: {}", dto.getId(), e.getMessage(), e);
            throw new RuntimeException("Error generando PDF", e);
        }
    }

    public byte[] generarPdf(GuiaUsuarioDTO dto) {
        return generarPdf(dto, "es");
    }

    public String sanitizeFilename(String titulo) {
        if (titulo == null || titulo.isBlank()) return "guia.pdf";
        String slug = titulo.trim().replaceAll("\\s+", "_").replaceAll("[^\\p{L}\\p{N}_\\-]", "");
        if (slug.isEmpty()) return "guia.pdf";
        if (slug.length() > 80) slug = slug.substring(0, 80);
        return slug + ".pdf";
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    // ── HTML builder ────────────────────────────────────────────────────

    private String buildHtml(GuiaUsuarioDTO dto, Locale locale) {
        GuiaSubvencionDTO guia = dto.getGuia();
        String fechaGen = LocalDate.now().format(DATE_FMT);
        StringBuilder sb = new StringBuilder(12000);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<style>\n");
        appendCss(sb, fechaGen, locale);
        sb.append("</style>\n</head>\n<body>\n");
        appendHeader(sb, dto, fechaGen, locale);
        if (guia != null) {
            appendResumen(sb, dto, guia, locale);
            appendMetodos(sb, guia, locale);
            appendDocumentos(sb, guia, locale);
            appendRequisitos(sb, guia, locale);
            appendPasos(sb, guia, locale);
            appendLegal(sb, guia, locale);
        }
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    // ── CSS ──────────────────────────────────────────────────────────────

    private void appendCss(StringBuilder sb, String fechaGen, Locale locale) {
        String genLabel = esc(msg("pdf.generated_on", locale)) + " " + esc(fechaGen);
        String pageFooter = buildPageFooterCss(locale);

        sb.append("@page {\n  size: A4;\n  margin: 20mm 20mm 26mm 20mm;\n");
        sb.append("  @bottom-left { content: \"Syntia\"; font-family: Helvetica, Arial, sans-serif; font-size: 8pt; color: #94a3b8; }\n");
        sb.append("  @bottom-center { content: ").append(pageFooter).append("; font-family: Helvetica, Arial, sans-serif; font-size: 8pt; color: #94a3b8; }\n");
        sb.append("  @bottom-right { content: \"").append(genLabel).append("\"; font-family: Helvetica, Arial, sans-serif; font-size: 8pt; color: #94a3b8; }\n");
        sb.append("}\n");

        sb.append("""
                body { font-family: Helvetica, Arial, sans-serif; font-size: 10pt; line-height: 1.6; color: #1e293b; margin: 0; padding: 0; }
                h1 { font-size: 20pt; color: #005a71; margin: 0 0 4pt 0; line-height: 1.2; }
                h2 { font-size: 13pt; color: #005a71; border-bottom: 2pt solid #005a71; padding-bottom: 5pt; margin: 24pt 0 10pt 0; }
                h3 { font-size: 11pt; color: #0f172a; margin: 4pt 0 2pt 0; }
                a { color: #0284c7; text-decoration: underline; }
                p { margin: 4pt 0; }
                .header-bar { background-color: #005a71; height: 4pt; margin-bottom: 18pt; }
                .brand { color: #005a71; font-size: 11pt; font-weight: bold; letter-spacing: 4pt; margin-bottom: 4pt; }
                .subtitle { color: #64748b; font-size: 11pt; margin-top: 2pt; }
                .meta-line { color: #64748b; font-size: 9pt; margin-top: 10pt; line-height: 1.4; }
                .separator { border-top: 1pt solid #e2e8f0; margin: 16pt 0; }
                .badge { display: inline-block; padding: 2pt 8pt; border-radius: 4pt; font-size: 9pt; font-weight: bold; }
                .badge-primary { background-color: #e0f2fe; color: #005a71; }
                .badge-cyan { background-color: #cffafe; color: #155e75; }
                .badge-violet { background-color: #ede9fe; color: #5b21b6; }
                .badge-amber { background-color: #fef3c7; color: #92400e; }
                .badge-sky { background-color: #e0f2fe; color: #0369a1; }
                .card { border: 1pt solid #e2e8f0; background-color: #f8fafc; border-radius: 6pt; padding: 10pt 12pt; margin-bottom: 8pt; }
                .card-accent { border-left: 3pt solid #005a71; }
                .card-label { font-size: 8pt; color: #64748b; font-weight: bold; text-transform: uppercase; letter-spacing: 0.5pt; margin-bottom: 3pt; }
                .card-value { font-size: 10pt; color: #1e293b; }
                .warning-box { border: 1pt solid #fbbf24; background-color: #fffbeb; border-left: 3pt solid #f59e0b; border-radius: 4pt; padding: 8pt 10pt; margin-bottom: 10pt; font-size: 9pt; }
                .legal-box { border: 1pt solid #cbd5e1; background-color: #f1f5f9; border-radius: 4pt; padding: 10pt 12pt; font-style: italic; color: #64748b; font-size: 9pt; margin-top: 8pt; }
                .step-row { page-break-inside: avoid; }
                .step-num { display: inline-block; width: 24pt; height: 24pt; line-height: 24pt; text-align: center; background-color: #005a71; color: white; border-radius: 12pt; font-weight: bold; font-size: 10pt; }
                .step-line { width: 2pt; background-color: #bae6fd; margin: 2pt auto 0 auto; }
                .check { color: #0284c7; font-weight: bold; margin-right: 4pt; }
                .muted { color: #64748b; }
                .small { font-size: 9pt; }
                .grid-table { width: 100%%; border-collapse: collapse; }
                .grid-table td { padding: 4pt 6pt; vertical-align: top; font-size: 10pt; }
                """);
    }

    // ── Header ──────────────────────────────────────────────────────────

    private void appendHeader(StringBuilder sb, GuiaUsuarioDTO dto, String fechaGen, Locale locale) {
        sb.append("<div class=\"header-bar\"></div>\n");
        sb.append("<div style=\"text-align: center; margin-bottom: 16pt;\">\n");
        sb.append("  <div class=\"brand\">SYNTIA</div>\n");
        sb.append("  <h1>").append(esc(nvl(dto.getTitulo(), msg("pdf.generated_with_ai", locale)))).append("</h1>\n");
        sb.append("  <div class=\"subtitle\">").append(esc(nvl(dto.getOrganismo(), ""))).append("</div>\n");

        if (dto.getPuntuacion() > 0) {
            sb.append("  <div style=\"margin: 10pt 0;\">\n");
            sb.append("    <span class=\"badge badge-primary\" style=\"font-size: 11pt; padding: 4pt 14pt;\">")
              .append(esc(msg("pdf.affinity", locale, dto.getPuntuacion()))).append("</span>\n");
            sb.append("  </div>\n");
        }

        sb.append("  <div class=\"meta-line\">");
        boolean first = true;
        first = appendMeta(sb, msg("pdf.project", locale), dto.getProyectoNombre(), first);
        first = appendMeta(sb, msg("pdf.location", locale), dto.getUbicacion(), first);
        first = appendMeta(sb, msg("pdf.sector", locale), dto.getSector(), first);
        if (dto.getFechaCierre() != null) {
            if (!first) sb.append(" &#160;|&#160; ");
            sb.append(esc(msg("pdf.closing", locale))).append(": ").append(dto.getFechaCierre().format(DATE_FMT));
            first = false;
        }
        if (!first) sb.append(" &#160;|&#160; ");
        sb.append(esc(msg("pdf.generated_on", locale))).append(": ").append(esc(fechaGen));
        sb.append("</div>\n</div>\n");
        sb.append("<div class=\"separator\"></div>\n");
    }

    private boolean appendMeta(StringBuilder sb, String label, String value, boolean first) {
        if (value == null || value.isBlank()) return first;
        if (!first) sb.append(" &#160;|&#160; ");
        sb.append(esc(label)).append(": ").append(esc(value));
        return false;
    }

    // ── Resumen ─────────────────────────────────────────────────────────

    private void appendResumen(StringBuilder sb, GuiaUsuarioDTO dto, GuiaSubvencionDTO guia, Locale locale) {
        sb.append("<h2>").append(esc(msg("pdf.section.summary", locale))).append("</h2>\n");
        GuiaSubvencionDTO.GrantSummary gs = guia.getGrantSummary();

        sb.append("<table style=\"width: 100%; border-collapse: separate; border-spacing: 6pt;\">\n<tr>\n");
        appendCard(sb, msg("pdf.label.organism", locale), nvl(dto.getOrganismo(), gs != null ? gs.getOrganism() : "-"), "50%");
        appendCard(sb, msg("pdf.label.beneficiaries", locale), gs != null && gs.getWhoCanApply() != null ? gs.getWhoCanApply() : "-", "50%");
        sb.append("</tr>\n</table>\n");

        sb.append("<div class=\"card card-accent\">\n");
        sb.append("  <div class=\"card-label\">").append(esc(msg("pdf.label.deadline", locale))).append("</div>\n");
        String plazo;
        if (dto.getFechaCierre() != null) {
            plazo = dto.getFechaCierre().format(DATE_FMT);
            if (gs != null && gs.getDeadline() != null && !gs.getDeadline().isBlank()) {
                plazo += " &#8212; " + esc(gs.getDeadline());
            }
        } else {
            plazo = esc(gs != null && gs.getDeadline() != null ? gs.getDeadline() : "-");
        }
        sb.append("  <div class=\"card-value\" style=\"font-size: 11pt; font-weight: bold; color: #005a71;\">").append(plazo).append("</div>\n");
        sb.append("</div>\n");

        if (gs != null && gs.getObjective() != null && !gs.getObjective().isBlank()) {
            sb.append("<div class=\"card\">\n");
            sb.append("  <div class=\"card-label\">").append(esc(msg("pdf.label.objective", locale))).append("</div>\n");
            sb.append("  <div class=\"card-value\">").append(esc(gs.getObjective())).append("</div>\n");
            sb.append("</div>\n");
        }
    }

    private void appendCard(StringBuilder sb, String label, String value, String width) {
        sb.append("<td style=\"width: ").append(width).append("; vertical-align: top;\"><div class=\"card\">\n");
        sb.append("  <div class=\"card-label\">").append(esc(label)).append("</div>\n");
        sb.append("  <div class=\"card-value\">").append(esc(value)).append("</div>\n");
        sb.append("</div></td>\n");
    }

    // ── Metodos ─────────────────────────────────────────────────────────

    private void appendMetodos(StringBuilder sb, GuiaSubvencionDTO guia, Locale locale) {
        List<GuiaSubvencionDTO.ApplicationMethod> methods = guia.getApplicationMethods();
        if (methods == null || methods.isEmpty()) return;

        sb.append("<h2>").append(esc(msg("pdf.section.methods", locale))).append("</h2>\n");
        for (int i = 0; i < methods.size(); i++) {
            GuiaSubvencionDTO.ApplicationMethod m = methods.get(i);
            sb.append("<div style=\"margin-bottom: 10pt; padding: 8pt; border: 1pt solid #e2e8f0; border-radius: 4pt; background-color: #f8fafc;\">\n");
            sb.append("  <span class=\"badge badge-cyan\">").append(i + 1).append("</span>\n");
            sb.append("  <strong style=\"margin-left: 6pt; color: #005a71;\">").append(esc(nvl(m.getMethod(), msg("pdf.label.method", locale)))).append("</strong>\n");
            if (m.getDescription() != null && !m.getDescription().isBlank()) {
                sb.append("  <p style=\"margin: 4pt 0 2pt 0;\">").append(esc(m.getDescription())).append("</p>\n");
            }
            if (m.getOfficialPortal() != null && !m.getOfficialPortal().isBlank()) {
                sb.append("  <p style=\"margin: 2pt 0;\"><a href=\"").append(esc(m.getOfficialPortal())).append("\">")
                  .append(esc(msg("pdf.label.official_portal", locale))).append(": ").append(esc(m.getOfficialPortal())).append("</a></p>\n");
            }
            sb.append("</div>\n");
        }
    }

    // ── Documentos ──────────────────────────────────────────────────────

    private void appendDocumentos(StringBuilder sb, GuiaSubvencionDTO guia, Locale locale) {
        List<String> docs = guia.getRequiredDocuments();
        if (docs == null || docs.isEmpty()) return;

        sb.append("<h2>").append(esc(msg("pdf.section.documents", locale))).append("</h2>\n");
        sb.append("<p class=\"muted small\" style=\"margin-bottom: 6pt;\">").append(esc(msg("pdf.label.documents_count", locale, docs.size()))).append("</p>\n");
        sb.append("<table class=\"grid-table\">\n");
        for (int i = 0; i < docs.size(); i++) {
            if (i % 2 == 0) sb.append("<tr>\n");
            sb.append("<td style=\"width: 50%; padding: 4pt 6pt;\"><span class=\"check\">&#10003;</span> ").append(esc(docs.get(i))).append("</td>\n");
            if (i % 2 == 1 || i == docs.size() - 1) {
                if (i % 2 == 0) sb.append("<td></td>\n");
                sb.append("</tr>\n");
            }
        }
        sb.append("</table>\n");
    }

    // ── Requisitos ──────────────────────────────────────────────────────

    private void appendRequisitos(StringBuilder sb, GuiaSubvencionDTO guia, Locale locale) {
        List<String> reqs = guia.getUniversalRequirementsLgsArt13();
        if (reqs == null || reqs.isEmpty()) return;

        sb.append("<h2>").append(esc(msg("pdf.section.requirements", locale))).append("</h2>\n");
        sb.append("<div class=\"warning-box\">\n");
        sb.append("  <strong>&#9888; ").append(esc(msg("pdf.label.lgs_title", locale))).append("</strong><br/>\n");
        sb.append("  ").append(esc(msg("pdf.label.lgs_desc", locale))).append("\n");
        sb.append("</div>\n");
        sb.append("<table class=\"grid-table\">\n");
        for (int i = 0; i < reqs.size(); i++) {
            if (i % 2 == 0) sb.append("<tr>\n");
            sb.append("<td style=\"width: 50%; padding: 4pt 6pt;\"><span class=\"check\">&#10003;</span> ").append(esc(reqs.get(i))).append("</td>\n");
            if (i % 2 == 1 || i == reqs.size() - 1) {
                if (i % 2 == 0) sb.append("<td></td>\n");
                sb.append("</tr>\n");
            }
        }
        sb.append("</table>\n");
    }

    // ── Pasos ───────────────────────────────────────────────────────────

    private void appendPasos(StringBuilder sb, GuiaSubvencionDTO guia, Locale locale) {
        List<GuiaSubvencionDTO.Workflow> workflows = guia.getWorkflows();
        if (workflows == null || workflows.isEmpty()) return;
        GuiaSubvencionDTO.Workflow wf = workflows.get(0);
        List<GuiaSubvencionDTO.WorkflowStep> steps = wf.getSteps();
        if (steps == null || steps.isEmpty()) return;

        sb.append("<h2>").append(esc(msg("pdf.section.steps", locale))).append("</h2>\n");
        if (wf.getMethod() != null && !wf.getMethod().isBlank()) {
            sb.append("<p class=\"muted small\">").append(esc(msg("pdf.label.method_label", locale)))
              .append(": <strong>").append(esc(wf.getMethod())).append("</strong></p>\n");
        }

        sb.append("<table style=\"width: 100%; border-collapse: collapse;\">\n");
        for (int i = 0; i < steps.size(); i++) {
            GuiaSubvencionDTO.WorkflowStep step = steps.get(i);
            sb.append("<tr class=\"step-row\">\n");

            sb.append("<td style=\"width: 36pt; vertical-align: top; text-align: center; padding: 4pt 0;\">\n");
            sb.append("  <div class=\"step-num\">").append(step.getStep() != null ? step.getStep() : i + 1).append("</div>\n");
            if (i < steps.size() - 1) {
                sb.append("  <div class=\"step-line\" style=\"min-height: 24pt;\">&#160;</div>\n");
            }
            sb.append("</td>\n");

            sb.append("<td style=\"padding: 4pt 0 18pt 10pt; vertical-align: top;\">\n");
            if (step.getPhase() != null && !step.getPhase().isBlank()) {
                sb.append("  <span class=\"badge badge-primary\" style=\"margin-bottom: 3pt;\">").append(esc(step.getPhase())).append("</span>\n");
            }
            sb.append("  <h3>").append(esc(nvl(step.getTitle(), msg("pdf.label.step", locale, i + 1)))).append("</h3>\n");
            if (step.getDescription() != null && !step.getDescription().isBlank()) {
                sb.append("  <p>").append(esc(step.getDescription())).append("</p>\n");
            }

            StringBuilder chips = new StringBuilder();
            if (step.getEstimatedTimeMinutes() != null && step.getEstimatedTimeMinutes() > 0) {
                chips.append("<span class=\"badge badge-violet\">&#9201; ")
                     .append(esc(msg("pdf.label.time_estimate", locale, step.getEstimatedTimeMinutes()))).append("</span> ");
            }
            if (step.getOfficialLink() != null && !step.getOfficialLink().isBlank()) {
                chips.append("<a href=\"").append(esc(step.getOfficialLink())).append("\" style=\"font-size: 9pt;\">")
                     .append(esc(step.getOfficialLink())).append("</a> ");
            }
            if (chips.length() > 0) {
                sb.append("  <div style=\"margin-top: 4pt;\">").append(chips).append("</div>\n");
            }

            List<String> stepDocs = step.getRequiredDocuments();
            if (stepDocs != null && !stepDocs.isEmpty()) {
                sb.append("  <div style=\"margin-top: 5pt;\">\n");
                for (String doc : stepDocs) {
                    sb.append("    <span class=\"badge badge-sky\" style=\"margin-right: 3pt; margin-bottom: 3pt;\">").append(esc(doc)).append("</span>\n");
                }
                sb.append("  </div>\n");
            }
            sb.append("</td>\n</tr>\n");
        }
        sb.append("</table>\n");
    }

    // ── Legal ───────────────────────────────────────────────────────────

    private void appendLegal(StringBuilder sb, GuiaSubvencionDTO guia, Locale locale) {
        GuiaSubvencionDTO.GrantSummary gs = guia.getGrantSummary();
        String disclaimer = guia.getLegalDisclaimer();
        String link = gs != null ? gs.getOfficialLink() : null;
        if ((link == null || link.isBlank()) && (disclaimer == null || disclaimer.isBlank())) return;

        sb.append("<h2>").append(esc(msg("pdf.section.legal", locale))).append("</h2>\n");
        if (link != null && !link.isBlank()) {
            sb.append("<p style=\"margin-bottom: 6pt;\"><a href=\"").append(esc(link)).append("\">")
              .append(esc(msg("pdf.label.official_link", locale))).append(": ").append(esc(link)).append("</a></p>\n");
        }
        if (disclaimer != null && !disclaimer.isBlank()) {
            sb.append("<div class=\"legal-box\">").append(esc(disclaimer)).append("</div>\n");
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String buildPageFooterCss(Locale locale) {
        String pageWord = msg("pdf.footer.page_word", locale);
        String ofWord = msg("pdf.footer.of_word", locale);
        return "\"" + esc(pageWord) + " \" counter(page) \" " + esc(ofWord) + " \" counter(pages)";
    }

    static String esc(String text) {
        if (text == null) return "";
        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD)) {
                clean.append(c);
            }
        }
        return clean.toString()
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String nvl(String text, String fallback) {
        return (text != null && !text.isBlank()) ? text : fallback;
    }
}
