package com.syntia.ai.model.dto;

/**
 * Resultado de una llamada a persistirNuevas():
 * cuántas convocatorias se insertaron, cuántas ya existían y cuántas se rechazaron por validación.
 */
public record ResultadoPersistencia(int nuevas, int duplicadas, int rechazadas) {

    public int procesadas() {
        return nuevas + duplicadas + rechazadas;
    }
}