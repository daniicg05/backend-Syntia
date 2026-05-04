package com.syntia.ai.service;

import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.FiltrosBdns;

public final class BdnsFiltrosBuilder {

    private BdnsFiltrosBuilder() {
    }

    public static FiltrosBdns construir(Proyecto proyecto) {
        String descripcion = resolverDescripcion(proyecto);

        String ubicacionRaw = proyecto.getUbicacion();
        Integer regionId = (ubicacionRaw != null && !ubicacionRaw.isBlank())
                ? UbicacionNormalizador.normalizarARegionId(ubicacionRaw)
                : null;

        String sectorRaw = proyecto.getSector();
        Integer finalidadId = SectorNormalizador.normalizarAFinalidadId(sectorRaw);

        return new FiltrosBdns(descripcion, regionId, finalidadId);
    }

    private static String resolverDescripcion(Proyecto proyecto) {
        String sector = proyecto.getSector();
        if (sector != null && !sector.isBlank()) {
            String normalizado = SectorNormalizador.normalizarABusqueda(sector);
            if (normalizado != null) return normalizado;
        }

        if (proyecto.getNombre() != null && !proyecto.getNombre().isBlank()) {
            String nombre = proyecto.getNombre().toLowerCase().trim();
            if (nombre.length() > 50) nombre = nombre.substring(0, 50);
            return "subvencion " + nombre;
        }

        return "subvencion pyme empresa";
    }
}
