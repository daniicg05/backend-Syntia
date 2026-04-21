package com.syntia.ai.controller.api;

import com.syntia.ai.config.SecurityConfig;
import com.syntia.ai.model.EstadoSolicitudFavorita;
import com.syntia.ai.model.dto.FavoritaResponseDTO;
import com.syntia.ai.security.JwtAuthenticationFilter;
import com.syntia.ai.service.CustomUserDetailService;
import com.syntia.ai.service.FavoritaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoritaController.class)
@Import(SecurityConfig.class)
class FavoritaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FavoritaService favoritaService;
    @MockBean
    private CustomUserDetailService customUserDetailService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void listar_sinAuth_devuelve401() throws Exception {
        mockMvc.perform(get("/api/usuario/favoritas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USUARIO")
    void listar_conAuth_devuelvePaginado() throws Exception {
        FavoritaResponseDTO item = FavoritaResponseDTO.builder()
                .id(10L)
                .titulo("Ayudas X")
                .estadoSolicitud(EstadoSolicitudFavorita.NO_SOLICITADA)
                .guardadaEn(LocalDateTime.now())
                .build();

        when(favoritaService.resolverUsuarioId("user@test.com")).thenReturn(1L);
        when(favoritaService.listar(anyLong(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/usuario/favoritas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].estadoSolicitud").value("no_solicitada"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USUARIO")
    void post_upsert_devuelveCreated() throws Exception {
        FavoritaResponseDTO favorita = FavoritaResponseDTO.builder().id(15L).titulo("Y").build();
        when(favoritaService.resolverUsuarioId("user@test.com")).thenReturn(1L);
        when(favoritaService.upsert(anyLong(), any()))
                .thenReturn(new FavoritaService.UpsertResult(favorita, true));

        mockMvc.perform(post("/api/usuario/favoritas")
                        .contentType("application/json")
                        .content("{\"convocatoriaId\":15,\"titulo\":\"Y\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(15));
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

