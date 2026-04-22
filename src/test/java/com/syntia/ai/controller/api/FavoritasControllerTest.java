package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.Rol;
import com.syntia.ai.model.Usuario;
import com.syntia.ai.security.JwtAuthenticationFilter;
import com.syntia.ai.service.ConvocatoriaFavoritaService;
import com.syntia.ai.service.CustomUserDetailService;
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

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoritasController.class)
@Import(SecurityConfig.class)
class FavoritasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConvocatoriaFavoritaService favoritaService;
    @MockBean
    private UsuarioService usuarioService;
    @MockBean
    private CustomUserDetailService customUserDetailService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
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

        Usuario usuario = Usuario.builder().id(9L).email("user@test.com").rol(Rol.USUARIO).build();
        when(usuarioService.buscarPorEmail("user@test.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USUARIO")
    void listarIds_devuelveFavoritasDelUsuario() throws Exception {
        when(favoritaService.listarIdsFavoritas(9L)).thenReturn(Set.of(11L, 22L));

        mockMvc.perform(get("/api/usuario/favoritas/ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USUARIO")
    void marcar_devuelveEstadoFavorita() throws Exception {
        mockMvc.perform(post("/api/usuario/favoritas/44"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convocatoriaId").value(44))
                .andExpect(jsonPath("$.favorita").value(true));

        verify(favoritaService).marcarFavorita(9L, 44L);
    }
}

