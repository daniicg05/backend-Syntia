package com.syntia.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstadoSolicitudFavoritaTest {

    @Test
    void fromValue_aceptaValoresValidos() {
        assertEquals(EstadoSolicitudFavorita.NO_SOLICITADA, EstadoSolicitudFavorita.fromValue("no_solicitada"));
        assertEquals(EstadoSolicitudFavorita.SOLICITADA, EstadoSolicitudFavorita.fromValue("solicitada"));
    }

    @Test
    void fromValue_lanzaErrorEnValorInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> EstadoSolicitudFavorita.fromValue("otro"));
    }
}

