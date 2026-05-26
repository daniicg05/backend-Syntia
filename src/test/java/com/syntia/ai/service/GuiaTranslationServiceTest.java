package com.syntia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.GuiaUsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GuiaTranslationServiceTest {

    private OpenAiClient openAiClient;
    private GuiaTranslationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        openAiClient = mock(OpenAiClient.class);
        service = new GuiaTranslationService(openAiClient);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private GuiaUsuarioDTO crearGuia() {
        GuiaSubvencionDTO.GrantSummary gs = GuiaSubvencionDTO.GrantSummary.builder()
                .title("Ayudas digitalizacion")
                .organism("Ministerio de Economia")
                .objective("Impulsar transformacion digital")
                .whoCanApply("Pymes")
                .deadline("31/12/2026")
                .officialLink("https://sede.mineco.gob.es")
                .build();
        GuiaSubvencionDTO guia = GuiaSubvencionDTO.builder()
                .grantSummary(gs)
                .requiredDocuments(List.of("DNI", "Certificado AEAT"))
                .legalDisclaimer("Guia orientativa")
                .build();
        return GuiaUsuarioDTO.builder()
                .id(1L).origen("analisis")
                .titulo("Ayudas digitalizacion")
                .organismo("Ministerio de Economia")
                .sector("Tecnologia").ubicacion("Nacional")
                .proyectoNombre("Mi proyecto")
                .fechaCierre(LocalDate.of(2026, 12, 31))
                .guia(guia).puntuacion(80)
                .creadoEn(LocalDateTime.now())
                .build();
    }

    @Test
    void translate_es_devuelveSinModificar() {
        GuiaUsuarioDTO dto = crearGuia();
        GuiaUsuarioDTO result = service.translate(dto, "es");
        assertSame(dto, result);
        verifyNoInteractions(openAiClient);
    }

    @Test
    void translate_en_llamaAlLlm_yTraduceMetadatosYGuia() throws Exception {
        GuiaUsuarioDTO dto = crearGuia();

        Map<String, Object> translatedPayload = Map.of(
                "metadata", Map.of(
                        "titulo", "Digitalization grants",
                        "organismo", "Ministry of Economy",
                        "sector", "Technology",
                        "ubicacion", "Nationwide",
                        "proyectoNombre", "My project"
                ),
                "guia", Map.of(
                        "grant_summary", Map.of(
                                "title", "Digitalization grants",
                                "organism", "Ministry of Economy",
                                "objective", "Boost digital transformation",
                                "who_can_apply", "SMEs",
                                "deadline", "31/12/2026",
                                "official_link", "https://sede.mineco.gob.es"
                        ),
                        "required_documents", List.of("ID Card", "AEAT Certificate"),
                        "legal_disclaimer", "Informative guide"
                )
        );
        String llmResponse = objectMapper.writeValueAsString(translatedPayload);
        when(openAiClient.chatAnalisis(anyString(), anyString())).thenReturn(llmResponse);

        GuiaUsuarioDTO result = service.translate(dto, "en");

        assertEquals("Digitalization grants", result.getTitulo());
        assertEquals("Ministry of Economy", result.getOrganismo());
        assertEquals("Technology", result.getSector());
        assertEquals("Digitalization grants", result.getGuia().getGrantSummary().getTitle());
        assertEquals("https://sede.mineco.gob.es", result.getGuia().getGrantSummary().getOfficialLink());
        assertEquals("ID Card", result.getGuia().getRequiredDocuments().get(0));

        verify(openAiClient, times(1)).chatAnalisis(anyString(), anyString());
    }

    @Test
    void translate_cache_segundaLlamadaNoInvocaLlm() throws Exception {
        GuiaUsuarioDTO dto = crearGuia();

        Map<String, Object> translatedPayload = Map.of(
                "metadata", Map.of("titulo", "Translated", "organismo", "", "sector", "", "ubicacion", "", "proyectoNombre", ""),
                "guia", Map.of("grant_summary", Map.of("title", "Translated"), "legal_disclaimer", "ok")
        );
        when(openAiClient.chatAnalisis(anyString(), anyString()))
                .thenReturn(objectMapper.writeValueAsString(translatedPayload));

        service.translate(dto, "en");
        service.translate(dto, "en");

        verify(openAiClient, times(1)).chatAnalisis(anyString(), anyString());
    }

    @Test
    void translate_llmFalla_devuelveOriginal() {
        GuiaUsuarioDTO dto = crearGuia();
        when(openAiClient.chatAnalisis(anyString(), anyString()))
                .thenThrow(new OpenAiClient.OpenAiUnavailableException("API down"));

        GuiaUsuarioDTO result = service.translate(dto, "en");

        assertSame(dto, result);
    }

    @Test
    void normalizeLang_valoresValidos() {
        assertEquals("es", GuiaTranslationService.normalizeLang("es"));
        assertEquals("en", GuiaTranslationService.normalizeLang("en"));
        assertEquals("ca", GuiaTranslationService.normalizeLang("ca"));
        assertEquals("gl", GuiaTranslationService.normalizeLang("gl"));
        assertEquals("eu", GuiaTranslationService.normalizeLang("eu"));
    }

    @Test
    void normalizeLang_fallbackAEs() {
        assertEquals("es", GuiaTranslationService.normalizeLang(null));
        assertEquals("es", GuiaTranslationService.normalizeLang(""));
        assertEquals("es", GuiaTranslationService.normalizeLang("fr"));
        assertEquals("es", GuiaTranslationService.normalizeLang("INVALID"));
    }
}
