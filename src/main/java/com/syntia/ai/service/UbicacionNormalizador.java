package com.syntia.ai.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Clase utilitaria para normalizar ubicaciones de texto libre a nombres oficiales de CCAA.
 * <p>
 * Las ubicaciones en Syntia se almacenan como texto libre (perfil y proyecto del usuario),
 * por lo que pueden contener variantes ortográficas, nombres en lenguas cooficiales,
 * abreviaturas, provincias o nombres informales. Esta clase mapea todas esas variantes
 * a los 19 valores exactos que acepta el parámetro {@code nivel2} de la API BDNS.
 * <p>
 * Devuelve {@code null} cuando la ubicación es nacional, no reconocida, vacía o null,
 * lo que indica al llamador que NO debe filtrar por CCAA en la búsqueda BDNS.
 *
 * @see BdnsClientService#buscarPorTextoFiltrado(String, Integer)
 */
public final class UbicacionNormalizador {

    private UbicacionNormalizador() {
        /** Clase utilitaria — no instanciable */
    }

    /**
     * Diccionario base de normalización.
     *
     * <p>La clave debe estar en minúsculas y sin espacios sobrantes para que coincida
     * con la forma en la que se procesa la entrada en `normalizarACcaa`.
     *
     * <p>El valor representa exactamente el literal esperado por `nivel2` en BDNS.
     */
    private static final Map<String, String> VARIANTES = new HashMap<>();

    static {
        /** ── Comunidad Valenciana ── */
        reg("valencia", "Comunidad Valenciana");
        reg("comunidad valenciana", "Comunidad Valenciana");
        reg("c. valenciana", "Comunidad Valenciana");
        reg("cv", "Comunidad Valenciana");
        reg("alicante", "Comunidad Valenciana");
        reg("castellon", "Comunidad Valenciana");
        reg("castellón", "Comunidad Valenciana");
        reg("valència", "Comunidad Valenciana");
        reg("valencia / valència", "Comunidad Valenciana");
        reg("castellón / castelló", "Comunidad Valenciana");
        reg("alicante / alacant", "Comunidad Valenciana");

        /** ── País Vasco ── */
        reg("euskadi", "País Vasco");
        reg("pais vasco", "País Vasco");
        reg("país vasco", "País Vasco");
        reg("euzkadi", "País Vasco");

        /** ── Cataluña ── */
        reg("catalunya", "Cataluña");
        reg("cataluña", "Cataluña");
        reg("catalonia", "Cataluña");

        /** ── Baleares ── */
        reg("baleares", "Baleares");
        reg("illes balears", "Baleares");
        reg("islas baleares", "Baleares");

        /** ── Castilla-La Mancha ── */
        reg("castilla la mancha", "Castilla-La Mancha");
        reg("castilla-la mancha", "Castilla-La Mancha");
        reg("clm", "Castilla-La Mancha");

        /** ── Castilla y León ── */
        reg("castilla leon", "Castilla y León");
        reg("castilla y leon", "Castilla y León");
        reg("castilla y león", "Castilla y León");
        reg("cyl", "Castilla y León");

        /** ── Madrid ── */
        reg("comunidad de madrid", "Madrid");
        reg("madrid", "Madrid");
        reg("cam", "Madrid");

        /** ── Asturias ── */
        reg("principado de asturias", "Asturias");
        reg("asturias", "Asturias");

        /** ── Murcia ── */
        reg("region de murcia", "Murcia");
        reg("región de murcia", "Murcia");
        reg("murcia", "Murcia");

        /** ── Navarra ── */
        reg("comunidad foral de navarra", "Navarra");
        reg("navarra", "Navarra");
        reg("nafarroa", "Navarra");

        /** ── Andalucía ── */
        reg("andalucia", "Andalucía");
        reg("andalucía", "Andalucía");

        /** ── Aragón ── */
        reg("aragon", "Aragón");
        reg("aragón", "Aragón");

        /** ── Canarias ── */
        reg("canarias", "Canarias");
        reg("islas canarias", "Canarias");

        /** ── Cantabria ── */
        reg("cantabria", "Cantabria");

        /** ── Extremadura ── */
        reg("extremadura", "Extremadura");

        /** ── Galicia ── */
        reg("galicia", "Galicia");
        reg("galiza", "Galicia");

        /** ── La Rioja ── */
        reg("la rioja", "La Rioja");
        reg("rioja", "La Rioja");

        /** ── Ceuta ── */
        reg("ceuta", "Ceuta");

        // ── Melilla ──
        reg("melilla", "Melilla");
    }

    /**
     * Conjunto de t´erminos que se interpretan como ´ambito estatal.
     *
     * <p>Si la entrada pertenece a este conjunto, el resultado es `null` para indicar
     * "sin filtro geográfico" al consumidor de esta utilidad.
     */
    private static final java.util.Set<String> NACIONALES = java.util.Set.of(
            "nacional", "españa", "spain", "estatal", "todas", ""
    );

    /**
     * Registra una equivalencia en el mapa de variantes.
     *
     * <p>Si se repite la misma clave, el último valor insertado sobrescribe el anterior.
     * Esto permite corregir prioridades de mapeo durante la inicialización estática.
     *
     * @param variante entrada canónica esperada tras `trim`\+`toLowerCase`
     * @param ccaaOficial nombre oficial de salida para BDNS
     */
    private static void reg(String variante, String ccaaOficial) {
        VARIANTES.put(variante, ccaaOficial);
    }

    /**
     * Normaliza una ubicación de texto libre al nombre oficial de CCAA reconocido por la API BDNS.
     * <p>
     * Devuelve una de las 19 CCAA oficiales si se reconoce la entrada, o {@code null} si la
     * ubicación es nacional, vacía, null o no reconocida. Un valor {@code null} indica al
     * llamador que no debe aplicar filtro geográfico en la búsqueda BDNS.
     *
     * @param ubicacionLibre texto libre de ubicación del usuario (puede ser null)
     * @return nombre oficial de CCAA (ej: "Comunidad Valenciana") o null si no aplica filtro
     */
    public static String normalizarACcaa(String ubicacionLibre) {
        if (ubicacionLibre == null) return null;
        String normalizada = ubicacionLibre.trim().toLowerCase();
        if (normalizada.isBlank() || NACIONALES.contains(normalizada)) return null;
        return VARIANTES.get(normalizada);
    }

    /**
     * Indica si la ubicación proporcionada se puede mapear a una CCAA reconocida.
     *
     * @param ubicacionLibre texto libre de ubicación del usuario (puede ser null)
     * @return {@code true} si {@link #normalizarACcaa(String)} devuelve un valor no null
     */
    public static boolean esCcaaReconocida(String ubicacionLibre) {
        return normalizarACcaa(ubicacionLibre) != null;
    }

    /** Mapa de nombre de CCAA normalizado → ID numérico del catálogo /api/regiones de BDNS. */
    private static final Map<String, Integer> CCAA_A_REGION_ID = new HashMap<>();

    static {
        CCAA_A_REGION_ID.put("galicia", 3);
        CCAA_A_REGION_ID.put("asturias", 8);
        CCAA_A_REGION_ID.put("cantabria", 10);
        CCAA_A_REGION_ID.put("país vasco", 13);
        CCAA_A_REGION_ID.put("navarra", 17);
        CCAA_A_REGION_ID.put("la rioja", 19);
        CCAA_A_REGION_ID.put("aragón", 21);
        CCAA_A_REGION_ID.put("madrid", 26);
        CCAA_A_REGION_ID.put("castilla y león", 29);
        CCAA_A_REGION_ID.put("castilla-la mancha", 39);
        CCAA_A_REGION_ID.put("extremadura", 45);
        CCAA_A_REGION_ID.put("cataluña", 49);
        CCAA_A_REGION_ID.put("comunidad valenciana", 54);
        CCAA_A_REGION_ID.put("baleares", 58);
        CCAA_A_REGION_ID.put("andalucía", 63);
        CCAA_A_REGION_ID.put("murcia", 72);
        CCAA_A_REGION_ID.put("ceuta", 74);
        CCAA_A_REGION_ID.put("melilla", 76);
        CCAA_A_REGION_ID.put("canarias", 79);
    }

    /**
     * Convierte una ubicación de texto libre al ID numérico de región del catálogo BDNS.
     * Primero normaliza a nombre de CCAA y luego resuelve el ID.
     *
     * @param ubicacionLibre texto libre de ubicación del usuario
     * @return ID numérico de la región BDNS o null si no se reconoce
     */
    public static Integer normalizarARegionId(String ubicacionLibre) {
        String ccaa = normalizarACcaa(ubicacionLibre);
        if (ccaa == null) return null;
        return CCAA_A_REGION_ID.get(ccaa.toLowerCase());
    }
}