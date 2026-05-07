package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.Convocatoria;
import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.repository.ProyectoRepository;
import com.syntia.ai.repository.RecomendacionRepository;
import com.syntia.ai.service.BdnsImportJobService;
import com.syntia.ai.service.ConvocatoriaService;
import com.syntia.ai.service.PerfilService;
import com.syntia.ai.service.ProyectoService;
import com.syntia.ai.service.RecomendacionService;
import com.syntia.ai.service.UsuarioService;
import com.syntia.ai.service.CustomUserDetailService;
import com.syntia.ai.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.syntia.ai.repository.SyncStateRepository;
import com.syntia.ai.service.BdnsEnrichmentService;
import com.syntia.ai.service.BdnsEtlPanelService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;


import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerDetalleUsuarioTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;
    @MockBean
    private CustomUserDetailService customUserDetailService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private PerfilService perfilService;
    @MockBean
    private ConvocatoriaService convocatoriaService;
    @MockBean
    private ProyectoService proyectoService;
    @MockBean
    private RecomendacionService recomendacionService;
    @MockBean
    private ProyectoRepository proyectoRepository;
    @MockBean
    private RecomendacionRepository recomendacionRepository;
    @MockBean
    private BdnsImportJobService bdnsImportJobService;

    @MockBean
    private BdnsEtlPanelService bdnsEtlPanelService;
    @MockBean
    private SyncStateRepository syncStateRepository;
    @MockBean
    private BdnsEnrichmentService bdnsEnrichmentService;
    @MockBean
    private com.syntia.ai.repository.ConvocatoriaRepository convocatoriaRepository;
    @MockBean
    private com.syntia.ai.repository.PerfilRepository perfilRepository2;
    @MockBean
    private com.syntia.ai.service.RegionService regionService;
    @MockBean
    private com.syntia.ai.service.CatalogoImportService catalogoImportService;
    @MockBean
    private com.syntia.ai.service.CatalogoJobService catalogoJobService;
    @MockBean
    private com.syntia.ai.service.IndiceConvocatoriaService indiceConvocatoriaService;
    @MockBean
    private com.syntia.ai.service.IndiceJobService indiceJobService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void detalleUsuario_devuelveContratoEsperado() throws Exception {
        Usuario usuario = Usuario.builder()
                .id(123L)
                .email("user@dominio.com")
                .rol(Rol.USUARIO)
                .password("hash")
                .creadoEn(LocalDateTime.of(2026, 4, 8, 10, 30))
                .build();

        Perfil perfil = Perfil.builder()
                .id(1L)
                .usuario(usuario)
                .sector("Tecnologia")
                .ubicacion("Madrid")
                .empresa("Mi Empresa SL")
                .provincia("Madrid")
                .telefono("+34 600000000")
                .build();

        Proyecto proyecto = Proyecto.builder()
                .id(1L)
                .usuario(usuario)
                .nombre("Proyecto A")
                .sector("Tecnologia")
                .build();

        when(usuarioService.buscarPorId(123L)).thenReturn(Optional.of(usuario));
        when(perfilService.obtenerPerfil(123L)).thenReturn(Optional.of(perfil));
        when(proyectoService.obtenerProyectos(123L)).thenReturn(List.of(proyecto));
        when(recomendacionService.contarPorProyecto(1L)).thenReturn(3L);
        when(usuarioService.emailCambiado(123L)).thenReturn(true);
        when(usuarioService.obtenerHistorialCorreo(123L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/usuarios/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(123))
                .andExpect(jsonPath("$.usuario.email").value("user@dominio.com"))
                .andExpect(jsonPath("$.usuario.empresa").value("Mi Empresa SL"))
                .andExpect(jsonPath("$.usuario.provincia").value("Madrid"))
                .andExpect(jsonPath("$.usuario.telefono").value("+34 600000000"))
                .andExpect(jsonPath("$.proyectos[0].id").value(1))
                .andExpect(jsonPath("$.proyectos[0].nombre").value("Proyecto A"))
                .andExpect(jsonPath("$.recsPerProyecto['1']").value(3))
                .andExpect(jsonPath("$.emailCambiado").value(true))
                .andExpect(jsonPath("$.historialCorreo").isArray())
                .andExpect(jsonPath("$.usuario.password").doesNotExist());
    }

    @Test
    void detalleUsuario_sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios/123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void detalleUsuario_conRolNoAdmin_devuelve403() throws Exception {
        when(usuarioService.buscarPorId(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/usuarios/123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listarConvocatorias_devuelvePaginaConTamanoFijo50() throws Exception {
        List<Convocatoria> convocatorias = IntStream.range(0, 50)
                .mapToObj(i -> Convocatoria.builder().id((long) i + 1).titulo("Convocatoria " + i).build())
                .toList();

        when(convocatoriaService.obtenerPagina(eq(0), eq(50)))
                .thenReturn(new PageImpl<>(convocatorias, PageRequest.of(0, 50), 120));

        mockMvc.perform(get("/api/admin/convocatorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convocatorias").isArray())
                .andExpect(jsonPath("$.convocatorias.length()").value(50))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(120))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listarConvocatorias_respondePaginaSolicitada() throws Exception {
        List<Convocatoria> convocatorias = IntStream.range(0, 20)
                .mapToObj(i -> Convocatoria.builder().id((long) i + 101).titulo("Convocatoria " + i).build())
                .toList();

        when(convocatoriaService.obtenerPagina(eq(2), eq(50)))
                .thenReturn(new PageImpl<>(convocatorias, PageRequest.of(2, 50), 120));

        mockMvc.perform(get("/api/admin/convocatorias").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convocatorias.length()").value(20))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * Hace que el mock del filtro JWT no interrumpa la cadena de filtros.
     * Sin esto, MockMvc puede devolver 200 con body vacío y romper las aserciones.
     */
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
