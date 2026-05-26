package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.model.dto.GuiaUsuarioDTO;
import com.syntia.ai.repository.AnalisisConvocatoriaRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.security.JwtAuthenticationFilter;
import com.syntia.ai.service.CustomUserDetailService;
import com.syntia.ai.service.GuiaPdfService;
import com.syntia.ai.service.GuiaTranslationService;
import com.syntia.ai.service.OpenAiGuiaService;
import com.syntia.ai.service.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GuiaUsuarioController.class)
@Import(SecurityConfig.class)
class GuiaUsuarioControllerPdfTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RecomendacionRepository recomendacionRepository;
    @MockBean private AnalisisConvocatoriaRepository analisisConvocatoriaRepository;
    @MockBean private OpenAiGuiaService openAiGuiaService;
    @MockBean private UsuarioService usuarioService;
    @MockBean private GuiaPdfService guiaPdfService;
    @MockBean private GuiaTranslationService guiaTranslationService;
    @MockBean private CustomUserDetailService customUserDetailService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final byte[] FAKE_PDF = "%PDF-1.4 fake".getBytes();

    @BeforeEach
    void configurarFiltroJwtMock() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));

        when(guiaTranslationService.translate(any(GuiaUsuarioDTO.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void configurarUsuarioMock() {
        Usuario u = Usuario.builder().id(1L).email("test@test.com").password("x").rol(Rol.USUARIO).build();
        when(usuarioService.buscarPorEmail("user")).thenReturn(Optional.of(u));
    }

    private void configurarRecomendacionMock() {
        Convocatoria conv = Convocatoria.builder()
                .id(10L).titulo("Ayudas digitalizacion").organismo("MINECO")
                .sector("Tecnologia").ubicacion("Nacional")
                .fechaCierre(LocalDate.of(2026, 12, 31))
                .numeroConvocatoria("123456").build();
        Proyecto proy = Proyecto.builder().id(5L).nombre("Mi proyecto")
                .usuario(Usuario.builder().id(1L).email("test@test.com").password("x").rol(Rol.USUARIO).build())
                .build();
        Recomendacion rec = Recomendacion.builder()
                .id(42L).convocatoria(conv).proyecto(proy)
                .puntuacion(85).guiaEnriquecida("{}")
                .generadaEn(LocalDateTime.now()).build();
        when(recomendacionRepository.findByIdAndUsuarioIdConGuia(42L, 1L))
                .thenReturn(Optional.of(rec));
        when(openAiGuiaService.deserializarGuia(anyString()))
                .thenReturn(GuiaSubvencionDTO.builder()
                        .grantSummary(GuiaSubvencionDTO.GrantSummary.builder().title("Test").build())
                        .requiredDocuments(List.of("DNI")).build());
        when(guiaPdfService.generarPdf(any(), anyString())).thenReturn(FAKE_PDF);
        when(guiaPdfService.sanitizeFilename(anyString())).thenReturn("test.pdf");
    }

    @Test
    @WithMockUser(username = "user", roles = "USUARIO")
    void descargarPdf_200_sinLang() throws Exception {
        configurarUsuarioMock();
        configurarRecomendacionMock();
        mockMvc.perform(get("/api/usuario/guias/recomendacion/42/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USUARIO")
    void descargarPdf_200_conLangEn() throws Exception {
        configurarUsuarioMock();
        configurarRecomendacionMock();
        mockMvc.perform(get("/api/usuario/guias/recomendacion/42/pdf").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USUARIO")
    void descargarPdf_404_cuandoNoExiste() throws Exception {
        configurarUsuarioMock();
        when(recomendacionRepository.findByIdAndUsuarioIdConGuia(anyLong(), eq(1L)))
                .thenReturn(Optional.empty());
        mockMvc.perform(get("/api/usuario/guias/recomendacion/999/pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "user", roles = "USUARIO")
    void descargarPdf_400_conOrigenInvalido() throws Exception {
        configurarUsuarioMock();
        mockMvc.perform(get("/api/usuario/guias/invalido/1/pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void descargarPdf_401_sinAuth() throws Exception {
        mockMvc.perform(get("/api/usuario/guias/recomendacion/1/pdf"))
                .andExpect(status().isUnauthorized());
    }
}
