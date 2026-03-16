package com.syntia.ai.model.dto;

/**
 * DTO inmutable que encapsula los filtros de búsqueda para BDNS.
 *
 * <p>Este `record` representa tres criterios opcionales:</p>
 * <ul>
 * <li>`descripcion`: filtro textual libre.</li>
 * <li>`nivel1`: filtro territorial de primer nivel.</li>
 * <li>`nivel2`: filtro territorial de segundo nivel, dependiente del contexto territorial.</li>
 * </ul>
 *
 * <p>Convenciones de uso:</p>
 * <ul>
 * <li>Un valor `null` indica que el criterio no está informado.</li>
 * <li>La instancia es inmutable; para quitar filtros se generan nuevas instancias.</li>
 * </ul>
 *
 * @param descripcion texto descriptivo para filtrar resultados; `null` si no aplica
 * @param nivel1 identificador o valor del territorio de nivel1; `null` si no aplica
 * @param nivel2 identificador o valor del territorio de nivel2; `null` si no aplica
 */
public record FiltrosBdns(
        String descripcion,
        String nivel1,
        String nivel2) {

    /**
     * Indica si existe al menos un criterio de filtrado informado.
     *
     * <p>Se considera que hay filtro cuando cualquiera de los tres campos es distinto de `null`.</p>
     *
     * @return `true` si hay al menos un filtro activo; `false` en caso contrario
     */
    public boolean tieneAlgunFiltro() {
        return (descripcion != null || nivel1 != null || nivel2 != null);
    }

    /**
     * Indica si hay filtro territorial activo.
     *
     * <p>La presencia territorial se determina por `nivel1`.</p>
     *
     * @return `true` si `nivel1` es distinto de `null`; `false` en caso contrario
     */
    public boolean tieneFiltroTerritorial() {
        return nivel1 != null;
    }

    /**
     * Devuelve una nueva instancia sin filtro de descripción.
     *
     * <p>Conserva los valores actuales de `nivel1` y `nivel2`.</p>
     *
     * @return nueva instancia con `descripcion = null`
     */
    public FiltrosBdns sinDescripción() {
        return new FiltrosBdns(null, nivel1, nivel2);
    }

    /**
     * Devuelve una nueva instancia sin filtros territoriales.
     *
     * <p>Conserva el valor actual de `descripcion` y limpia `nivel1` y `nivel2`.</p>
     *
     * @return nueva instancia con `nivel1 = null` y `nivel2 = null`
     */
    public FiltrosBdns sinTerritorio() {
        return new FiltrosBdns(descripcion, null, null);
    }
}