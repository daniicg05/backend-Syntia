package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.repository.*;
import com.syntia.ai.security.JwtAuthenticationFilter;
import com.syntia.ai.service.BdnsClientService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    @MockBean
    private IdxConvocatoriaBeneficiarioRepository beneficiarioRepository;
    @MockBean
    private IdxConvocatoriaFinalidadRepository finalidadIdxRepository;
    @MockBean
    private IdxConvocatoriaInstrumentoRepository instrumentoIdxRepository;
    @MockBean
    private IdxConvocatoriaOrganoRepository organoIdxRepository;
    @MockBean
    private IdxConvocatoriaRegionRepository regionIdxRepository;
    @MockBean
    private IdxConvocatoriaTipoAdminRepository tipoAdminIdxRepository;
    @MockBean
    private IdxConvocatoriaActividadRepository actividadIdxRepository;
    @MockBean
    private IdxConvocatoriaReglamentoRepository reglamentoIdxRepository;
    @MockBean
    private IdxConvocatoriaObjetivoRepository objetivoIdxRepository;
    @MockBean
    private IdxConvocatoriaSectorProductoRepository sectorProductoIdxRepository;
    @MockBean
    private RegionService regionService;

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
        when(bdnsClientService.obtenerDetalleLive("123456"))
                .thenReturn(Map.of(
                        "descripcion", "Ayudas para modernizacion empresarial",
                        "codigoBDNS", "123456",
                        "organo", Map.of("nivel1", "ESTADO", "nivel2", "MINISTERIO", "nivel3", "SECRETARIA"),
                        "sectores", List.of(Map.of("descripcion", "Empresa")),
                        "tiposBeneficiarios", List.of(
                                Map.of("descripcion", "Pymes"),
                                Map.of("descripcion", "Autonomos")
                        ),
                        "tipoConvocatoria", "Concurrencia competitiva",
                        "abierto", true
                ));

        mockMvc.perform(get("/api/convocatorias/publicas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.live").value(true))
                .andExpect(jsonPath("$.idBdns").value("123456"))
                .andExpect(jsonPath("$.sector").value("Empresa"))
                .andExpect(jsonPath("$.descripcion").value("Ayudas para modernizacion empresarial"))
                .andExpect(jsonPath("$.tiposBeneficiario[0]").value("Pymes"))
                .andExpect(jsonPath("$.tiposBeneficiario[1]").value("Autonomos"))
                .andExpect(jsonPath("$.organoNivel1").value("ESTADO"))
                .andExpect(jsonPath("$.tipoConvocatoria").value("Concurrencia competitiva"));
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
        when(bdnsClientService.obtenerDetalleLive("777")).thenReturn(null);

        mockMvc.perform(get("/api/convocatorias/publicas/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroConvocatoria").value("777"))
                .andExpect(jsonPath("$.sector").value("Tecnologia"))
                .andExpect(jsonPath("$.descripcion").value("Subvencion para digitalizacion"))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.tiposBeneficiario").isArray())
                .andExpect(jsonPath("$.tiposBeneficiario.length()").value(0));
    }

    @Test
    void detalle_merge_localGanaSiEsMasRico() throws Exception {
        Convocatoria convocatoria = Convocatoria.builder()
                .id(12L)
                .numeroConvocatoria("555")
                .idBdns("333")
                .sector("Local sector")
                .descripcion("Desc local")
                .build();

        when(convocatoriaRepository.findById(12L)).thenReturn(Optional.of(convocatoria));

        // Live devuelve 1 beneficiario
        when(bdnsClientService.obtenerDetalleLive("555"))
                .thenReturn(Map.of(
                        "descripcion", "Desc live",
                        "tiposBeneficiarios", List.of(Map.of("descripcion", "Pymes")),
                        "abierto", true
                ));

        // Local tiene 3 beneficiarios (más rico) → local debe ganar
        when(beneficiarioRepository.findBeneficiariosByNumeros(Set.of("555")))
                .thenReturn(List.of(
                        new Object[]{"555", "Pymes"},
                        new Object[]{"555", "Autonomos"},
                        new Object[]{"555", "Grandes empresas"}
                ));

        mockMvc.perform(get("/api/convocatorias/publicas/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.live").value(true))
                .andExpect(jsonPath("$.descripcion").value("Desc live"))
                .andExpect(jsonPath("$.tiposBeneficiario.length()").value(3))
                .andExpect(jsonPath("$.tiposBeneficiario[2]").value("Grandes empresas"));
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

