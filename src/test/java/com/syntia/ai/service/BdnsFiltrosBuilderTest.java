package com.syntia.ai.service;

import com.syntia.ai.model.dto.FiltrosBdns;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BdnsFiltrosBuilderTest {

    private BdnsFiltrosBuilder builder;

    @BeforeEach
    void setUp() {
        CatalogosBdnsService catalogos = Mockito.mock(CatalogosBdnsService.class);
        builder = new BdnsFiltrosBuilder(catalogos);
    }

    @Test
    void buildUrl_conFiltrosCompletos_generaUrlCorrecta() {
        FiltrosBdns f = FiltrosBdns.builder()
                .regiones(List.of(55, 9))
                .finalidad(14)
                .tipoAdministracion("A")
                .descripcion("innovacion")
                .descripcionTipoBusqueda(1)
                .fechaDesde(LocalDate.of(2026, 1, 1))
                .mrr(true)
                .build();

        String url = builder.buildUrl(f);

        assertThat(url).contains("vpd=GE");
        assertThat(url).contains("finalidad=14");
        assertThat(url).contains("tipoAdministracion=A");
        assertThat(url).contains("mrr=true");
        assertThat(url).contains("fechaDesde=2026-01-01");
    }

    @Test
    void buildUrl_camposNulos_noApareceEnUrl() {
        FiltrosBdns f = FiltrosBdns.builder().build();
        String url = builder.buildUrl(f);

        assertThat(url).doesNotContain("regiones");
        assertThat(url).doesNotContain("finalidad");
        assertThat(url).doesNotContain("mrr");
        assertThat(url).contains("vpd=GE");
        assertThat(url).contains("page=0");
    }
}

