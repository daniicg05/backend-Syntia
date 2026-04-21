package com.syntia.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoSolicitudFavorita {
    NO_SOLICITADA("no_solicitada"),
    SOLICITADA("solicitada");

    private final String value;

    EstadoSolicitudFavorita(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EstadoSolicitudFavorita fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("estadoSolicitud es obligatorio");
        }
        String normalized = value.trim().toLowerCase();
        for (EstadoSolicitudFavorita estado : values()) {
            if (estado.value.equals(normalized)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("estadoSolicitud invalido: " + value);
    }
}

