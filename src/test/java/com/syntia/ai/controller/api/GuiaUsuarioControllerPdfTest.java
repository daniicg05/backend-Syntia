package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.AnalisisConvocatoria;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Recomendacion;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.model.dto.GuiaSubvencionDTO;
import com.syntia.ai.repository.AnalisisConvocatoriaRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.security.JwtAuthenticationFilter;
import com.syntia.ai.service.CustomUserDetailService;
import com.syntia.ai.service.GuiaPdfService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GuiaUsuarioController.class)
@Import(SecurityConfig.class)
class GuiaUsuarioControllerPdfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private RecomendacionRepository recomendacionRepository;
    @MockBean private AnalisisConvocatoriaRepository analisisConvocatoriaRepository;
    @MockBean private OpenAiGuiaService openAiGuiaService;
    @MockBean private UsuarioService usuarioService;
    @MockBean private GuiaPdfService guiaPdfService;
    @MockBean private CustomUserDetailService customUserDetailService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final byte[] FAKE_PDF = "%PDF-1.4 fake".getBytes();

    @BeforeEach
    void configurarFiltroJwtMock() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class),
                any(FilterChain.class)
        );
    }

    private void configurarUsuarioMock() {
        Usuario usuario = Usuario.builder().id(1L).email("test@test.com").password("x").rol(Rol.USUARIO).build();
        when(usuarioService.buscarPorEmail("user")).thenReturn(Optional.of(usuario));
    }

    private Recomendacion crearRecomendacionMock() {
        Convocatoria conv = Convocatoria.builder()
                .id(10L).titulo("Ayudas digitalizacion").organismo("MINECO")
                .sector("Tecnologia").ubicacion("Nacional")
                .fechaCierre(LocalDate.of(2026, 12, 31))
                .numeroConvocatoria("123456").build();
        Proyecto proy = Proyecto.builder().id(5L).nombre("Mi proyecto")
                .usuario(Usuario.builder().id(1L).email("test@test.com").password("x").rol(Rol.USUARIO).build())
                .build();
        return Recomendacion.builder()
                .id(42L).convocatoria(conv).proyecto(proy)
                .puntuacion(85).guiaEnriquecida("{\"grant_summary\":{\"title\":\"Test\"}}")
                .generadaEn(LocalDateTime.now()).build();
    }

    @Test
    @WithMockUser(username = "user", roles = "USUARIO")
    void descargarPdf_200_conContentTypeCorrecto() throws Exception {
        configurarUsuarioMock();
        Recomendacion rec = crearRecomendacionMock();
        when(recomendacionRepository.findByIdAndUsuarioIdConGuia(42L, 1L))
                .thenReturn(Optional.of(rec));
        when(openAiGuiaService.deserializarGuia(anyString()))
                .thenReturn(GuiaSubvencionDTO.builder()
                        .grantSummary(GuiaSubvencionDTO.GrantSummary.builder()
                                .title("Test").organism("MINECO").build())
                        .requiredDocuments(List.of("DNI"))
                        .build());
        when(guiaPdfService.generarPdf(any())).thenReturn(FAKE_PDF);
        when(guiaPdfService.sanitizeFilename(anyString())).thenReturn("Ayudas_digitalizacion.pdf");

        mockMvc.perform(get("/api/usuario/guias/recomendacion/42/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Ayudas_digitalizacion.pdf\""));
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
