package com.syntia.ai.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class EstadoSolicitudFavoritaConverter implements AttributeConverter<EstadoSolicitudFavorita, String> {

    @Override
    public String convertToDatabaseColumn(EstadoSolicitudFavorita attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public EstadoSolicitudFavorita convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EstadoSolicitudFavorita.fromValue(dbData);
    }
}

