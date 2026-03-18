package com.syntia.ai.service;


import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.FiltrosBdns;

public final class BdnsFiltrosBuilder  {
    private BdnsFiltrosBuilder(){

    }

    public static FiltrosBdns construir(Proyecto proyecto, Perfil perfil){
        String descripcion = resolverDescripcion(proyecto, perfil);

        String ubicacionRaw = resolverUbicacion(proyecto, perfil);
        String ccaa = UbicacionNormalizador.normalizarACcaa(ubicacionRaw);

        return new FiltrosBdns(descripcion, null, ccaa);
    }

    private static String resolverDescripcion(Proyecto proyecto, Perfil perfil) {
        // 1. Sector del proyecto
        String sector = proyecto.getSector();
        if (sector != null && !sector.isBlank()) {
            String normalizado = SectorNormalizador.normalizarABusqueda(sector);
            if (normalizado != null) return normalizado;
        }

        // 2. Sector del perfil (fallback)
        if (perfil != null && perfil.getSector() != null && !perfil.getSector().isBlank()) {
            String normalizado = SectorNormalizador.normalizarABusqueda(perfil.getSector());
            if (normalizado != null) return normalizado;
        }

        // 3. Nombre del proyecto como último recurso
        if (proyecto.getNombre() != null && !proyecto.getNombre().isBlank()) {
            String nombre = proyecto.getNombre().toLowerCase().trim();
            if (nombre.length() > 50) nombre = nombre.substring(0, 50);
            return "subvencion " + nombre;
        }

        // 4. Fallback genérico
        return "subvencion pyme empresa";
    }

    private static String resolverUbicacion(Proyecto proyecto, Perfil perfil){
        if (proyecto.getUbicacion() != null && !proyecto.getUbicacion().isBlank()){
            return proyecto.getUbicacion();
        }

        if (perfil != null && perfil.getUbicacion() != null && !perfil.getUbicacion().isBlank()){
            return perfil.getUbicacion();
        }
        return null;
    }
}
