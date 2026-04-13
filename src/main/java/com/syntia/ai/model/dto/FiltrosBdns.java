package com.syntia.ai.model.dto;

/**
 * DTO inmutable que encapsula los filtros de búsqueda para la API BDNS moderna.
 *
 * @param descripcion  texto libre para el parámetro {@code descripcion} de BDNS
 * @param regionId     ID numérico de la región según catálogo {@code /api/regiones} de BDNS;
 *                     null indica sin filtro geográfico
 * @param finalidadId  ID numérico de la finalidad según catálogo {@code /api/finalidades};
 *                     null indica sin filtro de sector
 */
public record FiltrosBdns(
        String descripcion,
        Integer regionId,
        Integer finalidadId) {

    public boolean tieneAlgunFiltro() {
        return descripcion != null || regionId != null || finalidadId != null;
    }

    public boolean tieneFiltroTerritorial() {
        return regionId != null;
    }

    public FiltrosBdns sinDescripcion() {
        return new FiltrosBdns(null, regionId, finalidadId);
    }

    public FiltrosBdns sinTerritorio() {
        return new FiltrosBdns(descripcion, null, finalidadId);
    }
}
