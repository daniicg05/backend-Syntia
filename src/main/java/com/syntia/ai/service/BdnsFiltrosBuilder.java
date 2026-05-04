package com.syntia.ai.service;

import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.FiltrosBdns;

public final class BdnsFiltrosBuilder {

    private BdnsFiltrosBuilder() {
    }

    public static FiltrosBdns construir(Proyecto proyecto, Perfil perfil) {
        String descripcion = resolverDescripcion(proyecto, perfil);

        String ubicacionRaw = resolverUbicacion(proyecto, perfil);
        Integer regionId = UbicacionNormalizador.normalizarARegionId(ubicacionRaw);

        String sectorRaw = proyecto.getSector() != null ? proyecto.getSector()
                : (perfil != null ? perfil.getSector() : null);
        Integer finalidadId = SectorNormalizador.normalizarAFinalidadId(sectorRaw);

        return new FiltrosBdns(descripcion, regionId, finalidadId);
    }

    private static String resolverDescripcion(Proyecto proyecto, Perfil perfil) {
        String sector = proyecto.getSector();
        if (sector != null && !sector.isBlank()) {
            String normalizado = SectorNormalizador.normalizarABusqueda(sector);
            if (normalizado != null) return normalizado;
        }

        if (perfil != null && perfil.getSector() != null && !perfil.getSector().isBlank()) {
            String normalizado = SectorNormalizador.normalizarABusqueda(perfil.getSector());
            if (normalizado != null) return normalizado;
        }

        if (proyecto.getNombre() != null && !proyecto.getNombre().isBlank()) {
            String nombre = proyecto.getNombre().toLowerCase().trim();
            if (nombre.length() > 50) nombre = nombre.substring(0, 50);
            return "subvencion " + nombre;
        }

        return "subvencion pyme empresa";
    }

    private static String resolverUbicacion(Proyecto proyecto, Perfil perfil) {
        if (proyecto.getUbicacion() != null && !proyecto.getUbicacion().isBlank()) {
            return proyecto.getUbicacion();
        }
        if (perfil != null && perfil.getUbicacion() != null && !perfil.getUbicacion().isBlank()) {
            return perfil.getUbicacion();
        }
        return null;
    }
}
