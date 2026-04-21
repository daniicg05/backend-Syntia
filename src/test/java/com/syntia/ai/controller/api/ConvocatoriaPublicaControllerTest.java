package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.dto.ConvocatoriaDTO;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.security.JwtAuthenticationFilter;
import com.syntia.ai.service.ConvocatoriaService;
import com.syntia.ai.service.CustomUserDetailService;
import com.syntia.ai.service.RegionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConvocatoriaPublicaController.class)
@Import(SecurityConfig.class)
class ConvocatoriaPublicaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConvocatoriaRepository convocatoriaRepository;
    @MockBean
    private ConvocatoriaService convocatoriaService;
    @MockBean
    private RegionService regionService;
    @MockBean
    private CustomUserDetailService customUserDetailService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void detalle_publico_devuelveCamposEnriquecidos() throws Exception {
        Convocatoria convocatoria = Convocatoria.builder()
                .id(10L)
                .titulo("Ayudas para modernizacion empresarial")
                .tipo("Subvencion")
                .sector(null)
                .descripcion(null)
                .build();

        ConvocatoriaDTO dto = new ConvocatoriaDTO();
        dto.setId(10L);
        dto.setTitulo("Ayudas para modernizacion empresarial");
        dto.setTipo("Subvencion");
        dto.setSector("Empresa");
        dto.setDescripcion("Descripcion completa");

        when(convocatoriaRepository.findById(10L)).thenReturn(Optional.of(convocatoria));
        when(convocatoriaService.toDTO(convocatoria)).thenReturn(dto);

        mockMvc.perform(get("/api/convocatorias/publicas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.titulo").value("Ayudas para modernizacion empresarial"))
                .andExpect(jsonPath("$.tipo").value("Subvencion"))
                .andExpect(jsonPath("$.sector").value("Empresa"))
                .andExpect(jsonPath("$.descripcion").value("Descripcion completa"));
    }

    @Test
    void detalle_publico_devuelve_dtoCompleto() throws Exception {
        Convocatoria convocatoria = Convocatoria.builder()
                .id(11L)
                .titulo("Subvencion para digitalizacion")
                .sector("Tecnologia")
                .descripcion("Subvencion para digitalizacion")
                .build();

        ConvocatoriaDTO dto = new ConvocatoriaDTO();
        dto.setId(11L);
        dto.setTitulo("Subvencion para digitalizacion");
        dto.setSector("Tecnologia");
        dto.setDescripcion("Subvencion para digitalizacion");
        dto.setIdBdns("444");
        dto.setNumeroConvocatoria("777");

        when(convocatoriaRepository.findById(11L)).thenReturn(Optional.of(convocatoria));
        when(convocatoriaService.toDTO(convocatoria)).thenReturn(dto);

        mockMvc.perform(get("/api/convocatorias/publicas/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.titulo").value("Subvencion para digitalizacion"))
                .andExpect(jsonPath("$.sector").value("Tecnologia"))
                .andExpect(jsonPath("$.descripcion").value("Subvencion para digitalizacion"))
                .andExpect(jsonPath("$.numeroConvocatoria").value("777"));
    }

    @Test
    void detalle_publico_devuelve404_siNoExiste() throws Exception {
        when(convocatoriaRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/convocatorias/publicas/999"))
                .andExpect(status().isNotFound());
    }

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
}

