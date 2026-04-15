package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.repository.ConvocatoriaRepository;
import com.syntia.ai.security.JwtAuthenticationFilter;
import com.syntia.ai.service.BdnsClientService;
import com.syntia.ai.service.CustomUserDetailService;
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

import java.util.List;
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
    private BdnsClientService bdnsClientService;
    @MockBean
    private CustomUserDetailService customUserDetailService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void detalle_publico_devuelveCamposEnriquecidos() throws Exception {
        Convocatoria convocatoria = Convocatoria.builder()
                .id(10L)
                .numeroConvocatoria("123456")
                .idBdns("9988")
                .sector(null)
                .descripcion(null)
                .build();

        when(convocatoriaRepository.findById(10L)).thenReturn(Optional.of(convocatoria));
        when(bdnsClientService.obtenerDetalleConvocatoria("123456"))
                .thenReturn(new BdnsClientService.DetalleConvocatoriaBdns(
                        "Ayudas para modernizacion empresarial",
                        "Empresa",
                        List.of("Pymes", "Autonomos")
                ));

        mockMvc.perform(get("/api/convocatorias/publicas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.codigoBdns").value("123456"))
                .andExpect(jsonPath("$.sector").value("Empresa"))
                .andExpect(jsonPath("$.descripcion").value("Ayudas para modernizacion empresarial"))
                .andExpect(jsonPath("$.tiposBeneficiario[0]").value("Pymes"))
                .andExpect(jsonPath("$.tiposBeneficiario[1]").value("Autonomos"));
    }

    @Test
    void detalle_publico_haceFallbackABdLocal_siNoHayDetalleBdns() throws Exception {
        Convocatoria convocatoria = Convocatoria.builder()
                .id(11L)
                .numeroConvocatoria("777")
                .idBdns("444")
                .sector("Tecnologia")
                .descripcion("Subvencion para digitalizacion")
                .build();

        when(convocatoriaRepository.findById(11L)).thenReturn(Optional.of(convocatoria));
        when(bdnsClientService.obtenerDetalleConvocatoria("777")).thenReturn(null);

        mockMvc.perform(get("/api/convocatorias/publicas/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoBdns").value("777"))
                .andExpect(jsonPath("$.sector").value("Tecnologia"))
                .andExpect(jsonPath("$.descripcion").value("Subvencion para digitalizacion"))
                .andExpect(jsonPath("$.tiposBeneficiario").isArray())
                .andExpect(jsonPath("$.tiposBeneficiario.length()").value(0));
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

