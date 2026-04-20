package com.syntia.ai.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.util.Collections;
import java.util.List;

/**
 * Utilidad para deserializar campos TEXT que contienen JSON serializado
 * (arrays BDNS: instrumentos, tiposBeneficiarios, sectores, regiones,
 * fondos, objetivos, anuncios, documentos, sectoresProductos).
 *
 * Nunca lanza excepciones: si el JSON es null, vacío o malformado,
 * devuelve una lista vacía para que el DTO no rompa la respuesta.
 */
public final class JsonListParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonListParser() {}

    /**
     * Deserializa un JSON-array almacenado como String a una List<T>.
     * Devuelve lista vacía si el input es nulo, vacío o no parseable.
     */
    public static <T> List<T> parse(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Variante conveniente para listas de String.
     */
    public static List<String> parseStringList(String json) {
        return parse(json, new TypeReference<List<String>>() {});
    }
}