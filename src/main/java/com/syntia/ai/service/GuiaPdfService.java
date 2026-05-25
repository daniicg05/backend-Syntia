package com.syntia.ai.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.GuiaUsuarioDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class GuiaPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generarPdf(GuiaUsuarioDTO dto) {
        String html = buildHtml(dto);
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

    public String sanitizeFilename(String titulo) {
        if (titulo == null || titulo.isBlank()) return "guia.pdf";
        String slug = titulo.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^\\p{L}\\p{N}_\\-]", "");
        if (slug.isEmpty()) return "guia.pdf";
        if (slug.length() > 80) slug = slug.substring(0, 80);
        return slug + ".pdf";
    }

    private String buildHtml(GuiaUsuarioDTO dto) {
        GuiaSubvencionDTO guia = dto.getGuia();
        String fechaGen = LocalDate.now().format(DATE_FMT);

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<style>\n");
        appendCss(sb, fechaGen);
        sb.append("</style>\n</head>\n<body>\n");

        appendHeader(sb, dto, fechaGen);
        if (guia != null) {
            appendResumen(sb, dto, guia);
            appendMetodos(sb, guia);
            appendDocumentos(sb, guia);
            appendRequisitos(sb, guia);
            appendPasos(sb, guia);
            appendLegal(sb, guia);
        }

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    // ── CSS ──────────────────────────────────────────────────────────────

    private void appendCss(StringBuilder sb, String fechaGen) {
        sb.append("@page {\n");
        sb.append("  size: A4;\n");
        sb.append("  margin: 20mm 20mm 26mm 20mm;\n");
        sb.append("  @bottom-left {\n");
        sb.append("    content: \"Syntia\";\n");
        sb.append("    font-family: Helvetica, Arial, sans-serif;\n");
        sb.append("    font-size: 8pt; color: #6b7280;\n");
        sb.append("  }\n");
        sb.append("  @bottom-center {\n");
        sb.append("    content: \"P\\E1gina \" counter(page) \" de \" counter(pages);\n");
        sb.append("    font-family: Helvetica, Arial, sans-serif;\n");
        sb.append("    font-size: 8pt; color: #6b7280;\n");
        sb.append("  }\n");
        sb.append("  @bottom-right {\n");
        sb.append("    content: \"Generado el ").append(esc(fechaGen)).append("\";\n");
        sb.append("    font-family: Helvetica, Arial, sans-serif;\n");
        sb.append("    font-size: 8pt; color: #6b7280;\n");
        sb.append("  }\n");
        sb.append("}\n");

        sb.append("body { font-family: Helvetica, Arial, sans-serif; font-size: 10pt; line-height: 1.5; color: #111827; margin: 0; padding: 0; }\n");
        sb.append("h1 { font-size: 20pt; color: #111827; margin: 0 0 4pt 0; }\n");
        sb.append("h2 { font-size: 13pt; color: #111827; border-bottom: 2px solid #10b981; padding-bottom: 5pt; margin: 22pt 0 10pt 0; }\n");
        sb.append("h3 { font-size: 11pt; color: #374151; margin: 4pt 0 2pt 0; }\n");
        sb.append("a { color: #2563eb; text-decoration: underline; }\n");
        sb.append("p { margin: 4pt 0; }\n");

        sb.append(".brand { color: #10b981; font-size: 13pt; font-weight: bold; letter-spacing: 3pt; margin-bottom: 6pt; }\n");
        sb.append(".subtitle { color: #6b7280; font-size: 11pt; margin-top: 2pt; }\n");
        sb.append(".meta-line { color: #6b7280; font-size: 9pt; margin-top: 8pt; }\n");
        sb.append(".separator { border-top: 1px solid #e5e7eb; margin: 14pt 0; }\n");

        sb.append(".badge { display: inline-block; padding: 2pt 8pt; border-radius: 4pt; font-size: 9pt; font-weight: bold; }\n");
        sb.append(".badge-emerald { background-color: #d1fae5; color: #065f46; }\n");
        sb.append(".badge-violet { background-color: #ede9fe; color: #5b21b6; }\n");
        sb.append(".badge-amber { background-color: #fef3c7; color: #92400e; }\n");
        sb.append(".badge-teal { background-color: #ccfbf1; color: #134e4a; }\n");
        sb.append(".badge-gray { background-color: #f3f4f6; color: #374151; }\n");

        sb.append(".card { border: 1px solid #e5e7eb; background-color: #f9fafb; border-radius: 6pt; padding: 10pt; margin-bottom: 8pt; }\n");
        sb.append(".card-accent { border-left: 3pt solid #10b981; }\n");
        sb.append(".card-label { font-size: 9pt; color: #6b7280; font-weight: bold; text-transform: uppercase; letter-spacing: 0.5pt; margin-bottom: 3pt; }\n");
        sb.append(".card-value { font-size: 10pt; color: #111827; }\n");

        sb.append(".warning-box { border: 1px solid #f59e0b; background-color: #fffbeb; border-left: 3pt solid #f59e0b; border-radius: 4pt; padding: 8pt 10pt; margin-bottom: 10pt; }\n");
        sb.append(".legal-box { border: 1px solid #d1d5db; background-color: #f3f4f6; border-radius: 4pt; padding: 10pt; font-style: italic; color: #6b7280; font-size: 9pt; margin-top: 8pt; }\n");

        sb.append(".step-row { page-break-inside: avoid; }\n");
        sb.append(".step-num { display: inline-block; width: 22pt; height: 22pt; line-height: 22pt; text-align: center; background-color: #10b981; color: white; border-radius: 11pt; font-weight: bold; font-size: 10pt; }\n");

        sb.append(".check { color: #10b981; font-weight: bold; margin-right: 4pt; }\n");
        sb.append(".muted { color: #6b7280; }\n");
        sb.append(".small { font-size: 9pt; }\n");
        sb.append(".grid-table { width: 100%; border-collapse: collapse; }\n");
        sb.append(".grid-table td { padding: 3pt 6pt; vertical-align: top; font-size: 10pt; }\n");
    }

    // ── Portada / cabecera ──────────────────────────────────────────────

    private void appendHeader(StringBuilder sb, GuiaUsuarioDTO dto, String fechaGen) {
        sb.append("<div style=\"text-align: center; margin-bottom: 16pt;\">\n");
        sb.append("  <div class=\"brand\">SYNTIA</div>\n");
        sb.append("  <h1>").append(esc(nvl(dto.getTitulo(), "Guia de solicitud"))).append("</h1>\n");
        sb.append("  <div class=\"subtitle\">").append(esc(nvl(dto.getOrganismo(), ""))).append("</div>\n");

        if (dto.getPuntuacion() > 0) {
            sb.append("  <div style=\"margin: 8pt 0;\">\n");
            sb.append("    <span class=\"badge badge-emerald\" style=\"font-size: 11pt; padding: 3pt 12pt;\">")
              .append(dto.getPuntuacion()).append("% afinidad</span>\n");
            sb.append("  </div>\n");
        }

        sb.append("  <div class=\"meta-line\">");
        boolean first = true;
        if (dto.getProyectoNombre() != null && !dto.getProyectoNombre().isBlank()) {
            sb.append("Proyecto: ").append(esc(dto.getProyectoNombre()));
            first = false;
        }
        if (dto.getUbicacion() != null && !dto.getUbicacion().isBlank()) {
            if (!first) sb.append(" &#160;|&#160; ");
            sb.append("Ubicaci&#243;n: ").append(esc(dto.getUbicacion()));
            first = false;
        }
        if (dto.getSector() != null && !dto.getSector().isBlank()) {
            if (!first) sb.append(" &#160;|&#160; ");
            sb.append("Sector: ").append(esc(dto.getSector()));
            first = false;
        }
        if (dto.getFechaCierre() != null) {
            if (!first) sb.append(" &#160;|&#160; ");
            sb.append("Cierre: ").append(dto.getFechaCierre().format(DATE_FMT));
            first = false;
        }
        if (!first) sb.append(" &#160;|&#160; ");
        sb.append("Generado el: ").append(esc(fechaGen));
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("<div class=\"separator\"></div>\n");
    }

    // ── Resumen ejecutivo ───────────────────────────────────────────────

    private void appendResumen(StringBuilder sb, GuiaUsuarioDTO dto, GuiaSubvencionDTO guia) {
        sb.append("<h2>Resumen ejecutivo</h2>\n");

        GuiaSubvencionDTO.GrantSummary gs = guia.getGrantSummary();

        sb.append("<table style=\"width: 100%; border-collapse: separate; border-spacing: 6pt;\">\n<tr>\n");
        // Organismo card
        sb.append("<td style=\"width: 50%; vertical-align: top;\">\n");
        sb.append("  <div class=\"card\">\n");
        sb.append("    <div class=\"card-label\">Organismo</div>\n");
        sb.append("    <div class=\"card-value\">").append(esc(nvl(dto.getOrganismo(), gs != null ? gs.getOrganism() : "-"))).append("</div>\n");
        sb.append("  </div>\n</td>\n");
        // Beneficiarios card
        sb.append("<td style=\"width: 50%; vertical-align: top;\">\n");
        sb.append("  <div class=\"card\">\n");
        sb.append("    <div class=\"card-label\">Beneficiarios</div>\n");
        sb.append("    <div class=\"card-value\">").append(esc(gs != null && gs.getWhoCanApply() != null ? gs.getWhoCanApply() : "-")).append("</div>\n");
        sb.append("  </div>\n</td>\n");
        sb.append("</tr>\n</table>\n");

        // Plazo (highlighted)
        sb.append("<div class=\"card card-accent\">\n");
        sb.append("  <div class=\"card-label\">Plazo</div>\n");
        String plazo = gs != null && gs.getDeadline() != null ? gs.getDeadline() : "-";
        if (dto.getFechaCierre() != null) {
            plazo = dto.getFechaCierre().format(DATE_FMT);
            if (gs != null && gs.getDeadline() != null && !gs.getDeadline().isBlank()) {
                plazo += " &#8212; " + esc(gs.getDeadline());
            }
        } else {
            plazo = esc(plazo);
        }
        sb.append("  <div class=\"card-value\" style=\"font-size: 11pt; font-weight: bold; color: #065f46;\">").append(plazo).append("</div>\n");
        sb.append("</div>\n");

        // Objetivo
        if (gs != null && gs.getObjective() != null && !gs.getObjective().isBlank()) {
            sb.append("<div class=\"card\">\n");
            sb.append("  <div class=\"card-label\">Objetivo</div>\n");
            sb.append("  <div class=\"card-value\">").append(esc(gs.getObjective())).append("</div>\n");
            sb.append("</div>\n");
        }
    }

    // ── Metodos de solicitud ────────────────────────────────────────────

    private void appendMetodos(StringBuilder sb, GuiaSubvencionDTO guia) {
        List<GuiaSubvencionDTO.ApplicationMethod> methods = guia.getApplicationMethods();
        if (methods == null || methods.isEmpty()) return;

        sb.append("<h2>M&#233;todos de solicitud</h2>\n");
        for (int i = 0; i < methods.size(); i++) {
            GuiaSubvencionDTO.ApplicationMethod m = methods.get(i);
            sb.append("<div style=\"margin-bottom: 10pt; padding-left: 6pt;\">\n");
            sb.append("  <span class=\"badge badge-teal\">").append(i + 1).append("</span>\n");
            sb.append("  <strong style=\"margin-left: 4pt;\">").append(esc(nvl(m.getMethod(), "M&#233;todo"))).append("</strong>\n");
            if (m.getDescription() != null && !m.getDescription().isBlank()) {
                sb.append("  <p>").append(esc(m.getDescription())).append("</p>\n");
            }
            if (m.getOfficialPortal() != null && !m.getOfficialPortal().isBlank()) {
                sb.append("  <p><a href=\"").append(esc(m.getOfficialPortal())).append("\">Portal oficial: ")
                  .append(esc(m.getOfficialPortal())).append("</a></p>\n");
            }
            sb.append("</div>\n");
        }
    }

    // ── Documentos requeridos ───────────────────────────────────────────

    private void appendDocumentos(StringBuilder sb, GuiaSubvencionDTO guia) {
        List<String> docs = guia.getRequiredDocuments();
        if (docs == null || docs.isEmpty()) return;

        sb.append("<h2>Documentos requeridos</h2>\n");
        sb.append("<p class=\"muted small\" style=\"margin-bottom: 6pt;\">").append(docs.size()).append(" documentos</p>\n");
        sb.append("<table class=\"grid-table\">\n");
        for (int i = 0; i < docs.size(); i++) {
            if (i % 2 == 0) sb.append("<tr>\n");
            sb.append("<td style=\"width: 50%;\"><span class=\"check\">&#10003;</span> ").append(esc(docs.get(i))).append("</td>\n");
            if (i % 2 == 1 || i == docs.size() - 1) {
                if (i % 2 == 0) sb.append("<td></td>\n");
                sb.append("</tr>\n");
            }
        }
        sb.append("</table>\n");
    }

    // ── Requisitos universales (LGS art. 13) ───────────────────────────

    private void appendRequisitos(StringBuilder sb, GuiaSubvencionDTO guia) {
        List<String> reqs = guia.getUniversalRequirementsLgsArt13();
        if (reqs == null || reqs.isEmpty()) return;

        sb.append("<h2>Requisitos (LGS art. 13)</h2>\n");
        sb.append("<div class=\"warning-box\">\n");
        sb.append("  <strong>&#9888; Ley General de Subvenciones</strong><br/>\n");
        sb.append("  Los siguientes requisitos son obligatorios para todas las subvenciones p&#250;blicas espa&#241;olas (Ley 38/2003, art. 13).\n");
        sb.append("</div>\n");
        sb.append("<table class=\"grid-table\">\n");
        for (int i = 0; i < reqs.size(); i++) {
            if (i % 2 == 0) sb.append("<tr>\n");
            sb.append("<td style=\"width: 50%;\"><span class=\"check\">&#10003;</span> ").append(esc(reqs.get(i))).append("</td>\n");
            if (i % 2 == 1 || i == reqs.size() - 1) {
                if (i % 2 == 0) sb.append("<td></td>\n");
                sb.append("</tr>\n");
            }
        }
        sb.append("</table>\n");
    }

    // ── Pasos a seguir (workflow) ───────────────────────────────────────

    private void appendPasos(StringBuilder sb, GuiaSubvencionDTO guia) {
        List<GuiaSubvencionDTO.Workflow> workflows = guia.getWorkflows();
        if (workflows == null || workflows.isEmpty()) return;
        GuiaSubvencionDTO.Workflow wf = workflows.get(0);
        List<GuiaSubvencionDTO.WorkflowStep> steps = wf.getSteps();
        if (steps == null || steps.isEmpty()) return;

        sb.append("<h2>Pasos a seguir</h2>\n");
        if (wf.getMethod() != null && !wf.getMethod().isBlank()) {
            sb.append("<p class=\"muted small\">M&#233;todo: <strong>").append(esc(wf.getMethod())).append("</strong></p>\n");
        }

        sb.append("<table style=\"width: 100%; border-collapse: collapse;\">\n");
        for (int i = 0; i < steps.size(); i++) {
            GuiaSubvencionDTO.WorkflowStep step = steps.get(i);
            sb.append("<tr class=\"step-row\">\n");

            // Number column with vertical line
            sb.append("<td style=\"width: 34pt; vertical-align: top; text-align: center; padding: 4pt 0;\">\n");
            sb.append("  <div class=\"step-num\">").append(step.getStep() != null ? step.getStep() : i + 1).append("</div>\n");
            if (i < steps.size() - 1) {
                sb.append("  <div style=\"width: 2pt; height: 100%; min-height: 20pt; background-color: #d1fae5; margin: 2pt auto 0 auto;\">&#160;</div>\n");
            }
            sb.append("</td>\n");

            // Content column
            sb.append("<td style=\"padding: 4pt 0 16pt 8pt; vertical-align: top;\">\n");
            if (step.getPhase() != null && !step.getPhase().isBlank()) {
                sb.append("  <span class=\"badge badge-emerald\">").append(esc(step.getPhase())).append("</span>\n");
            }
            sb.append("  <h3>").append(esc(nvl(step.getTitle(), "Paso " + (i + 1)))).append("</h3>\n");
            if (step.getDescription() != null && !step.getDescription().isBlank()) {
                sb.append("  <p>").append(esc(step.getDescription())).append("</p>\n");
            }

            // Time estimate + phase badges row
            boolean hasChips = false;
            StringBuilder chips = new StringBuilder();
            if (step.getEstimatedTimeMinutes() != null && step.getEstimatedTimeMinutes() > 0) {
                chips.append("<span class=\"badge badge-violet\">&#9201; ").append(step.getEstimatedTimeMinutes()).append(" min</span> ");
                hasChips = true;
            }
            if (step.getOfficialLink() != null && !step.getOfficialLink().isBlank()) {
                chips.append("<a href=\"").append(esc(step.getOfficialLink())).append("\" style=\"font-size: 9pt;\">")
                     .append(esc(step.getOfficialLink())).append("</a> ");
                hasChips = true;
            }
            if (hasChips) {
                sb.append("  <div style=\"margin-top: 4pt;\">").append(chips).append("</div>\n");
            }

            // Required documents for this step
            List<String> stepDocs = step.getRequiredDocuments();
            if (stepDocs != null && !stepDocs.isEmpty()) {
                sb.append("  <div style=\"margin-top: 4pt;\">\n");
                for (String doc : stepDocs) {
                    sb.append("    <span class=\"badge badge-emerald\" style=\"margin-right: 4pt; margin-bottom: 2pt;\">").append(esc(doc)).append("</span>\n");
                }
                sb.append("  </div>\n");
            }

            sb.append("</td>\n</tr>\n");
        }
        sb.append("</table>\n");
    }

    // ── Informacion legal ───────────────────────────────────────────────

    private void appendLegal(StringBuilder sb, GuiaSubvencionDTO guia) {
        GuiaSubvencionDTO.GrantSummary gs = guia.getGrantSummary();
        String disclaimer = guia.getLegalDisclaimer();
        String link = gs != null ? gs.getOfficialLink() : null;

        if ((link == null || link.isBlank()) && (disclaimer == null || disclaimer.isBlank())) return;

        sb.append("<h2>Informaci&#243;n legal</h2>\n");
        if (link != null && !link.isBlank()) {
            sb.append("<p><a href=\"").append(esc(link)).append("\">Enlace oficial: ").append(esc(link)).append("</a></p>\n");
        }
        if (disclaimer != null && !disclaimer.isBlank()) {
            sb.append("<div class=\"legal-box\">").append(esc(disclaimer)).append("</div>\n");
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    static String esc(String text) {
        if (text == null) return "";
        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0x9 || c == 0xA || c == 0xD
                    || (c >= 0x20 && c <= 0xD7FF)
                    || (c >= 0xE000 && c <= 0xFFFD)) {
                clean.append(c);
            }
        }
        return clean.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String nvl(String text, String fallback) {
        return (text != null && !text.isBlank()) ? text : fallback;
    }
}
