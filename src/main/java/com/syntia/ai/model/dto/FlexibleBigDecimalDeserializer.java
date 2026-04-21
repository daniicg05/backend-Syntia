package com.syntia.ai.model.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

public class FlexibleBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_FLOAT || p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return p.getDecimalValue();
        }

        String raw = p.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String sanitized = raw.trim()
                .replace("€", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".");

        try {
            return new BigDecimal(sanitized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("presupuesto invalido: " + raw);
        }
    }
}

