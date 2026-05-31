package com.syntia.ai.service;

import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.GuiaUsuarioDTO;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuiaPdfServiceTest {

    private final GuiaPdfService service;

    GuiaPdfServiceTest() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        this.service = new GuiaPdfService(ms);
    }

    private GuiaUsuarioDTO crearGuiaCompleta() {
        GuiaSubvencionDTO.GrantSummary gs = GuiaSubvencionDTO.GrantSummary.builder()
                .title("Ayudas para la digitalizacion empresarial")
                .organism("Ministerio de Economia")
                .objective("Impulsar la transformacion digital de las pymes")
                .whoCanApply("Pymes y autonomos")
                .deadline("31/12/2026")
                .officialLink("https://sede.mineco.gob.es/ayudas")
                .legalBasis("Ley 38/2003")
                .build();

        GuiaSubvencionDTO.ApplicationMethod metodo = GuiaSubvencionDTO.ApplicationMethod.builder()
                .method("Online")
                .description("A traves de la sede electronica del ministerio")
                .officialPortal("https://sede.mineco.gob.es")
                .build();

        GuiaSubvencionDTO.WorkflowStep paso1 = GuiaSubvencionDTO.WorkflowStep.builder()
                .step(1).phase("preparation").title("Reunir documentacion")
                .description("Recopile todos los documentos necesarios antes de iniciar la solicitud.")
                .estimatedTimeMinutes(30)
                .requiredDocuments(List.of("DNI", "Certificado AEAT"))
                .officialLink("https://sede.agenciatributaria.gob.es/")
                .build();

        GuiaSubvencionDTO.WorkflowStep paso2 = GuiaSubvencionDTO.WorkflowStep.builder()
                .step(2).phase("submission").title("Presentar solicitud")
                .description("Acceda a la sede electronica y complete el formulario.")
                .estimatedTimeMinutes(15)
                .build();

        GuiaSubvencionDTO.Workflow workflow = GuiaSubvencionDTO.Workflow.builder()
                .method("Online")
                .steps(List.of(paso1, paso2))
                .build();

        GuiaSubvencionDTO guia = GuiaSubvencionDTO.builder()
                .grantSummary(gs)
                .applicationMethods(List.of(metodo))
                .requiredDocuments(List.of("DNI", "Certificado AEAT", "Certificado TGSS", "Memoria del proyecto", "Plan de negocio"))
                .universalRequirementsLgsArt13(List.of(
                        "Certificado al corriente con AEAT",
                        "Certificado situacion cotizacion TGSS",
                        "Declaracion responsable art. 13.2 LGS",
                        "Sin deudas de reintegro de subvenciones"))
                .workflows(List.of(workflow))
                .legalDisclaimer("Guia orientativa generada con datos de BDNS. Verificar requisitos en convocatoria oficial.")
                .build();

        return GuiaUsuarioDTO.builder()
                .id(42L)
                .origen("recomendacion")
                .titulo("Ayudas para la digitalizacion empresarial")
                .organismo("Ministerio de Economia")
                .sector("Tecnologia")
                .ubicacion("Nacional")
                .fechaCierre(LocalDate.of(2026, 12, 31))
                .proyectoNombre("Proyecto Digitalizacion")
                .guia(guia)
                .creadoEn(LocalDateTime.of(2026, 5, 25, 10, 0))
                .puntuacion(85)
                .build();
    }

    @Test
    void generarPdf_devuelveBytesConFirmaPdf() {
        byte[] pdf = service.generarPdf(crearGuiaCompleta());
        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }

    @Test
    void generarPdf_conLangEs_firmaPdf() {
        byte[] pdf = service.generarPdf(crearGuiaCompleta(), "es");
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }

    @Test
    void generarPdf_conLangEn_firmaPdf() {
        byte[] pdf = service.generarPdf(crearGuiaCompleta(), "en");
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }

    @Test
    void generarPdf_conLangCa_firmaPdf() {
        byte[] pdf = service.generarPdf(crearGuiaCompleta(), "ca");
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }

    @Test
    void generarPdf_conGuiaMinima_sinExcepciones() {
        GuiaUsuarioDTO dto = GuiaUsuarioDTO.builder()
                .id(1L).origen("analisis").titulo("Test")
                .guia(GuiaSubvencionDTO.builder().build())
                .puntuacion(0).build();
        byte[] pdf = service.generarPdf(dto, "en");
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }

    @Test
    void sanitizeFilename_tituloNormal() {
        assertEquals("Ayudas_digitalizacion.pdf", service.sanitizeFilename("Ayudas digitalizacion"));
    }

    @Test
    void sanitizeFilename_tituloNulo() {
        assertEquals("guia.pdf", service.sanitizeFilename(null));
    }

    @Test
    void sanitizeFilename_tituloVacio() {
        assertEquals("guia.pdf", service.sanitizeFilename("   "));
    }

    @Test
    void sanitizeFilename_tituloLargo_recortaA80() {
        String largo = "A".repeat(100);
        String result = service.sanitizeFilename(largo);
        assertEquals(80 + ".pdf".length(), result.length());
    }
}
