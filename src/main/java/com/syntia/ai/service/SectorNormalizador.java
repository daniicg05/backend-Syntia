package com.syntia.ai.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Clase utilitaria para normalizar sectores de texto libre a términos de búsqueda BDNS.
 * <p>
 * Los campos {@code Proyecto.sector} y {@code Perfil.sector} son texto libre,
 * por lo que pueden contener variantes, sinónimos o descripciones largas.
 * Esta clase mapea las variantes más frecuentes a términos cortos y eficaces
 * para el parámetro {@code descripcion} de la API BDNS.
 * <p>
 * Si el sector no se reconoce, devuelve el texto original limpio (truncado a 60 chars)
 * como término de búsqueda genérico — funciona razonablemente bien en la API BDNS
 * porque el parámetro {@code descripcion} acepta texto libre.
 *
 * <p>La clase no mantiene estado mutable externo y centraliza reglas de
 * normalización para su reutilización desde servicios de aplicación.</p>
 *
 * @see BdnsFiltrosBuilder
 */
public final class SectorNormalizador {

    /**
     * Constructor privado para evitar instanciación.
     */
    private SectorNormalizador() {
        // Clase utilitaria — no instanciable
    }

    /** Mapa de variantes (lowercase, trimmed) → término de búsqueda BDNS.
     *
     * Diccionario principal de equivalencias:
     * clave \-> variante normalizada (minúsculas y sin espacios extremos),
     * valor \-> término de búsqueda recomendado para BDNS.
     */
    private static final Map<String, String> SECTORES = new HashMap<>();

    static {
        /** ── Tecnología e innovación ── */
        reg("tecnologia", "subvencion tecnologia innovacion");
        reg("tecnología", "subvencion tecnologia innovacion");
        reg("tic", "subvencion tecnologia digital");
        reg("informatica", "subvencion tecnologia digital");
        reg("informática", "subvencion tecnologia digital");
        reg("software", "subvencion tecnologia digital");
        reg("desarrollo web", "subvencion tecnologia digital");
        reg("inteligencia artificial", "subvencion inteligencia artificial innovacion");
        reg("ia", "subvencion inteligencia artificial innovacion");
        reg("ciberseguridad", "subvencion ciberseguridad digital");
        reg("telecomunicaciones", "subvencion telecomunicaciones digital");
        reg("blockchain", "subvencion tecnologia innovacion digital");

        /** ── Digitalización ── */
        reg("digitalizacion", "subvencion digitalizacion pyme");
        reg("digitalización", "subvencion digitalizacion pyme");
        reg("transformacion digital", "subvencion digitalizacion empresa");
        reg("transformación digital", "subvencion digitalizacion empresa");
        reg("comercio electronico", "subvencion comercio electronico digital");
        reg("ecommerce", "subvencion comercio electronico digital");

        /** ── Energía y medio ambiente ── */
        reg("energia", "subvencion energia renovable");
        reg("energía", "subvencion energia renovable");
        reg("energia renovable", "subvencion energia renovable sostenibilidad");
        reg("energía renovable", "subvencion energia renovable sostenibilidad");
        reg("solar", "subvencion energia solar renovable");
        reg("fotovoltaica", "subvencion energia solar fotovoltaica");
        reg("medio ambiente", "subvencion medio ambiente sostenibilidad");
        reg("medioambiente", "subvencion medio ambiente sostenibilidad");
        reg("sostenibilidad", "subvencion sostenibilidad medioambiental");
        reg("economia circular", "subvencion economia circular sostenibilidad");
        reg("economía circular", "subvencion economia circular sostenibilidad");
        reg("residuos", "subvencion gestion residuos medioambiente");

        /** ── Agroalimentario ── */
        reg("agricultura", "subvencion agricultura sector agrario");
        reg("ganaderia", "subvencion ganaderia sector agrario");
        reg("ganadería", "subvencion ganaderia sector agrario");
        reg("agroalimentario", "subvencion agroalimentario industria alimentaria");
        reg("alimentacion", "subvencion industria alimentaria");
        reg("alimentación", "subvencion industria alimentaria");
        reg("pesca", "subvencion pesca acuicultura");
        reg("forestal", "subvencion forestal medio rural");
        reg("vitivinicola", "subvencion vitivinicola sector agrario");

        /** ── Industria y construcción ── */
        reg("industria", "subvencion industria manufactura");
        reg("manufactura", "subvencion industria manufactura");
        reg("construccion", "subvencion construccion edificacion");
        reg("construcción", "subvencion construccion edificacion");
        reg("automocion", "subvencion automocion industria");
        reg("automoción", "subvencion automocion industria");

        /** ── Comercio y servicios ── */
        reg("comercio", "subvencion comercio minorista");
        reg("hosteleria", "subvencion hosteleria turismo");
        reg("hostelería", "subvencion hosteleria turismo");
        reg("restauracion", "subvencion hosteleria restauracion");
        reg("restauración", "subvencion hosteleria restauracion");
        reg("turismo", "subvencion turismo sector turistico");
        reg("servicios", "subvencion empresa servicios");
        reg("transporte", "subvencion transporte logistica");
        reg("logistica", "subvencion transporte logistica");
        reg("logística", "subvencion transporte logistica");

        /** ── Cultura y educación ── */
        reg("cultura", "subvencion cultura artes");
        reg("educacion", "subvencion educacion formacion");
        reg("educación", "subvencion educacion formacion");
        reg("formacion", "subvencion formacion profesional empleo");
        reg("formación", "subvencion formacion profesional empleo");

        /** ── Salud ── */
        reg("salud", "subvencion salud sanitario");
        reg("sanidad", "subvencion salud sanitario");
        reg("farmacia", "subvencion farmaceutico sanitario");
        reg("biotecnologia", "subvencion biotecnologia innovacion");
        reg("biotecnología", "subvencion biotecnologia innovacion");

        /** ── Social ── */
        reg("social", "subvencion social accion social");
        reg("ong", "subvencion entidad social sin animo lucro");
        reg("cooperacion", "subvencion cooperacion internacional desarrollo");
        reg("cooperación", "subvencion cooperacion internacional desarrollo");
        reg("igualdad", "subvencion igualdad genero");
        reg("discapacidad", "subvencion discapacidad inclusion");
        reg("inclusion", "subvencion inclusion social");
        reg("inclusión", "subvencion inclusion social");

        /** ── I+D+i ── */
        reg("investigacion", "subvencion investigacion desarrollo innovacion");
        reg("investigación", "subvencion investigacion desarrollo innovacion");
        reg("i+d", "subvencion investigacion desarrollo innovacion");
        reg("i+d+i", "subvencion investigacion desarrollo innovacion");
        reg("innovacion", "subvencion innovacion empresa");
        reg("innovación", "subvencion innovacion empresa");

        /** ── Emprendimiento ── */
        reg("emprendimiento", "subvencion emprendimiento creacion empresa");
        reg("startup", "subvencion startup emprendimiento innovacion");
        reg("autonomo", "subvencion autonomo trabajador cuenta propia");
        reg("autónomo", "subvencion autonomo trabajador cuenta propia");
        reg("pyme", "subvencion pyme pequena empresa");

        /** ── Internacionalización ── */
        reg("exportacion", "subvencion exportacion internacionalizacion");
        reg("exportación", "subvencion exportacion internacionalizacion");
        reg("internacionalizacion", "subvencion internacionalizacion empresa exterior");
        reg("internacionalización", "subvencion internacionalizacion empresa exterior");

        /** ── Empleo ── */
        reg("empleo", "subvencion empleo contratacion");
        reg("contratacion", "subvencion contratacion empleo");
        reg("contratación", "subvencion contratacion empleo");
        reg("juventud", "subvencion empleo joven juventud");
    }

    /** Mapa de sector normalizado → ID numérico del catálogo /api/finalidades de BDNS. */
    private static final Map<String, Integer> SECTOR_A_FINALIDAD_ID = new HashMap<>();

    static {
        // Tecnología e I+D+i → 17
        SECTOR_A_FINALIDAD_ID.put("tecnologia", 17);
        SECTOR_A_FINALIDAD_ID.put("tecnología", 17);
        SECTOR_A_FINALIDAD_ID.put("tic", 17);
        SECTOR_A_FINALIDAD_ID.put("informatica", 17);
        SECTOR_A_FINALIDAD_ID.put("informática", 17);
        SECTOR_A_FINALIDAD_ID.put("software", 17);
        SECTOR_A_FINALIDAD_ID.put("inteligencia artificial", 17);
        SECTOR_A_FINALIDAD_ID.put("ia", 17);
        SECTOR_A_FINALIDAD_ID.put("investigacion", 17);
        SECTOR_A_FINALIDAD_ID.put("investigación", 17);
        SECTOR_A_FINALIDAD_ID.put("i+d", 17);
        SECTOR_A_FINALIDAD_ID.put("i+d+i", 17);
        SECTOR_A_FINALIDAD_ID.put("innovacion", 17);
        SECTOR_A_FINALIDAD_ID.put("innovación", 17);
        SECTOR_A_FINALIDAD_ID.put("digitalizacion", 17);
        SECTOR_A_FINALIDAD_ID.put("digitalización", 17);
        SECTOR_A_FINALIDAD_ID.put("transformacion digital", 17);
        SECTOR_A_FINALIDAD_ID.put("transformación digital", 17);
        SECTOR_A_FINALIDAD_ID.put("blockchain", 17);
        SECTOR_A_FINALIDAD_ID.put("ciberseguridad", 17);
        // Comercio, turismo y PYMEs → 14
        SECTOR_A_FINALIDAD_ID.put("comercio", 14);
        SECTOR_A_FINALIDAD_ID.put("turismo", 14);
        SECTOR_A_FINALIDAD_ID.put("pyme", 14);
        SECTOR_A_FINALIDAD_ID.put("hosteleria", 14);
        SECTOR_A_FINALIDAD_ID.put("hostelería", 14);
        SECTOR_A_FINALIDAD_ID.put("emprendimiento", 14);
        SECTOR_A_FINALIDAD_ID.put("startup", 14);
        SECTOR_A_FINALIDAD_ID.put("autonomo", 14);
        SECTOR_A_FINALIDAD_ID.put("autónomo", 14);
        SECTOR_A_FINALIDAD_ID.put("exportacion", 14);
        SECTOR_A_FINALIDAD_ID.put("exportación", 14);
        SECTOR_A_FINALIDAD_ID.put("internacionalizacion", 14);
        SECTOR_A_FINALIDAD_ID.put("internacionalización", 14);
        // Empleo → 6
        SECTOR_A_FINALIDAD_ID.put("empleo", 6);
        SECTOR_A_FINALIDAD_ID.put("contratacion", 6);
        SECTOR_A_FINALIDAD_ID.put("contratación", 6);
        SECTOR_A_FINALIDAD_ID.put("juventud", 6);
        // Educación → 10
        SECTOR_A_FINALIDAD_ID.put("educacion", 10);
        SECTOR_A_FINALIDAD_ID.put("educación", 10);
        SECTOR_A_FINALIDAD_ID.put("formacion", 10);
        SECTOR_A_FINALIDAD_ID.put("formación", 10);
        // Industria y energía → 13
        SECTOR_A_FINALIDAD_ID.put("industria", 13);
        SECTOR_A_FINALIDAD_ID.put("energia", 13);
        SECTOR_A_FINALIDAD_ID.put("energía", 13);
        SECTOR_A_FINALIDAD_ID.put("energia renovable", 13);
        SECTOR_A_FINALIDAD_ID.put("energía renovable", 13);
        SECTOR_A_FINALIDAD_ID.put("automocion", 13);
        SECTOR_A_FINALIDAD_ID.put("automoción", 13);
        SECTOR_A_FINALIDAD_ID.put("manufactura", 13);
        // Agricultura → 12
        SECTOR_A_FINALIDAD_ID.put("agricultura", 12);
        SECTOR_A_FINALIDAD_ID.put("ganaderia", 12);
        SECTOR_A_FINALIDAD_ID.put("ganadería", 12);
        SECTOR_A_FINALIDAD_ID.put("pesca", 12);
        SECTOR_A_FINALIDAD_ID.put("agroalimentario", 12);
        SECTOR_A_FINALIDAD_ID.put("alimentacion", 12);
        SECTOR_A_FINALIDAD_ID.put("alimentación", 12);
        // Sanidad → 9
        SECTOR_A_FINALIDAD_ID.put("salud", 9);
        SECTOR_A_FINALIDAD_ID.put("sanidad", 9);
        SECTOR_A_FINALIDAD_ID.put("farmacia", 9);
        SECTOR_A_FINALIDAD_ID.put("biotecnologia", 9);
        SECTOR_A_FINALIDAD_ID.put("biotecnología", 9);
        // Cultura → 11
        SECTOR_A_FINALIDAD_ID.put("cultura", 11);
        // Servicios sociales → 5
        SECTOR_A_FINALIDAD_ID.put("social", 5);
        SECTOR_A_FINALIDAD_ID.put("ong", 5);
        SECTOR_A_FINALIDAD_ID.put("discapacidad", 5);
        SECTOR_A_FINALIDAD_ID.put("inclusion", 5);
        SECTOR_A_FINALIDAD_ID.put("inclusión", 5);
        SECTOR_A_FINALIDAD_ID.put("cooperacion", 5);
        SECTOR_A_FINALIDAD_ID.put("cooperación", 5);
        // Transporte → 15
        SECTOR_A_FINALIDAD_ID.put("transporte", 15);
        SECTOR_A_FINALIDAD_ID.put("logistica", 15);
        SECTOR_A_FINALIDAD_ID.put("logística", 15);
        // Vivienda → 8
        SECTOR_A_FINALIDAD_ID.put("vivienda", 8);
        SECTOR_A_FINALIDAD_ID.put("construccion", 8);
        SECTOR_A_FINALIDAD_ID.put("construcción", 8);
    }

    /**
     * Convierte un sector de texto libre al ID numérico de finalidad del catálogo BDNS.
     *
     * @param sectorLibre texto libre del sector del usuario
     * @return ID numérico de finalidad BDNS o null si no se reconoce
     */
    public static Integer normalizarAFinalidadId(String sectorLibre) {
        if (sectorLibre == null || sectorLibre.isBlank()) return null;
        return SECTOR_A_FINALIDAD_ID.get(sectorLibre.toLowerCase().trim());
    }

    /**
     * Convierte un sector libre en un término de búsqueda para BDNS.
     *
     * <p>Flujo aplicado:</p>
     * <ol>
     * <li>Si el valor es nulo o vacío, retorna nulo.</li>
     * <li>Normaliza a minúsculas y trim.</li>
     * <li>Si existe en diccionario, retorna su equivalencia.</li>
     * <li>Si no existe, limpia caracteres no válidos y genera fallback con prefijo.</li>
     * </ol>
     *
     * @param sectorLibre texto de entrada del sector
     * @return término de búsqueda BDNS o nulo si no hay contenido utilizable
     */
    public static String normalizarABusqueda(String sectorLibre) {
        if (sectorLibre == null || sectorLibre.isBlank()) return null;

        /**
         * Clave normalizada para consulta exacta al diccionario.
         */
        String key = sectorLibre.toLowerCase().trim();

        /**
         * Resultado por mapeo directo.
         */
        String mapeado = SECTORES.get(key);
        if (mapeado != null) return mapeado;

        /**
         * Fallback de texto libre:
         * se eliminan símbolos no alfabéticos, se limita longitud * y se añade el prefijo "subvencion" para elevar relevancia.
         */
        String limpio = key.replaceAll("[^a-záéíóúüñ\\s]", "").trim();
        if (limpio.isBlank()) return null;
        if (limpio.length() > 60) limpio = limpio.substring(0, 60);
        return "subvencion " + limpio;
    }

    /**
     * Indica si el sector recibido está contemplado en el diccionario interno.
     *
     * @param sectorLibre texto libre del sector
     * @return `true` si existe mapeo exacto tras normalización básica
     */
    public static boolean esSectorReconocido(String sectorLibre) {
        if (sectorLibre == null || sectorLibre.isBlank()) return false;
        return SECTORES.containsKey(sectorLibre.toLowerCase().trim());
    }

    /**
     * Inserta una variante de sector y su término objetivo en el diccionario.
     *
     * @param variante forma de entrada esperada
     * @param termino término de búsqueda BDNS asociado
     */
    private static void reg(String variante, String termino) {
        SECTORES.put(variante.toLowerCase().trim(), termino);
    }
}