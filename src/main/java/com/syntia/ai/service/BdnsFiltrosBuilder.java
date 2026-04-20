package com.syntia.ai.service;

import com.syntia.ai.model.Perfil;
import com.syntia.ai.model.Proyecto;
import com.syntia.ai.model.dto.FiltrosBdns;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BdnsFiltrosBuilder {

    private static final String BDNS_BASE =
            "https://www.infosubvenciones.es/bdnstrans/api";

    private final CatalogosBdnsService catalogos;

    /**
     * Compatibilidad con el uso estatico existente.
     */
    public static FiltrosBdns construir(Proyecto proyecto, Perfil perfil) {
        String descripcion = resolverDescripcionLegacy(proyecto, perfil);

        String ubicacionRaw = resolverUbicacionLegacy(proyecto, perfil);
        Integer regionId = UbicacionNormalizador.normalizarARegionId(ubicacionRaw);

        String sectorRaw = proyecto.getSector() != null ? proyecto.getSector()
                : (perfil != null ? perfil.getSector() : null);
        Integer finalidadId = SectorNormalizador.normalizarAFinalidadId(sectorRaw);

        return FiltrosBdns.builder()
                .descripcion(descripcion)
                .regiones(regionId != null ? List.of(regionId) : null)
                .finalidad(finalidadId)
                .descripcionTipoBusqueda(1)
                .build();
    }

    /**
     * Construye FiltrosBdns desde proyecto+perfil resolviendo texto a IDs.
     * Toda la logica de normalizacion vive aqui.
     */
    public FiltrosBdns fromProyectoPerfil(Proyecto proyecto, Perfil perfil) {
        String ubicacion = coalesce(
                proyecto != null ? proyecto.getUbicacion() : null,
                perfil != null ? perfil.getUbicacion() : null
        );
        String sector = coalesce(
                proyecto != null ? proyecto.getSector() : null,
                perfil != null ? perfil.getSector() : null
        );

        List<Integer> regionIds = catalogos.resolverRegionIds(ubicacion);
        Integer finalidadId = catalogos.resolverFinalidadId(sector);
        String tipoAdmon = inferirTipoAdmon(ubicacion, regionIds);

        log.debug("[FILTROS] ubicacion='{}' regiones={} sector='{}' finalidad={}",
                ubicacion, regionIds, sector, finalidadId);

        return FiltrosBdns.builder()
                .pageSize(15)
                .descripcionTipoBusqueda(1)
                .regiones(regionIds.isEmpty() ? null : regionIds)
                .finalidad(finalidadId)
                .tipoAdministracion(tipoAdmon)
                .build();
    }

    /**
     * Construye URL completa para GET /convocatorias/busqueda.
     * Solo incluye parametros no nulos o no vacios.
     */
    public String buildUrl(FiltrosBdns f) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(BDNS_BASE + "/convocatorias/busqueda")
                .queryParam("vpd", f.getVpd())
                .queryParam("page", f.getPage())
                .queryParam("pageSize", f.getPageSize());

        if (hasText(f.getDescripcion())) {
            b.queryParam("descripcion", f.getDescripcion());
        }
        if (f.getDescripcionTipoBusqueda() != null) {
            b.queryParam("descripcionTipoBusqueda", f.getDescripcionTipoBusqueda());
        }
        if (hasText(f.getNumeroConvocatoria())) {
            b.queryParam("numeroConvocatoria", f.getNumeroConvocatoria());
        }
        if (hasText(f.getAyudaEstado())) {
            b.queryParam("ayudaEstado", f.getAyudaEstado());
        }
        if (Boolean.TRUE.equals(f.getMrr())) {
            b.queryParam("mrr", true);
        }
        if (Boolean.TRUE.equals(f.getContribucion())) {
            b.queryParam("contribucion", true);
        }
        if (f.getFechaDesde() != null) {
            b.queryParam("fechaDesde", f.getFechaDesde().toString());
        }
        if (f.getFechaHasta() != null) {
            b.queryParam("fechaHasta", f.getFechaHasta().toString());
        }
        if (hasText(f.getTipoAdministracion())) {
            b.queryParam("tipoAdministracion", f.getTipoAdministracion());
        }
        if (notEmpty(f.getRegiones())) {
            b.queryParam("regiones", joinIds(f.getRegiones()));
        }
        if (notEmpty(f.getOrganos())) {
            b.queryParam("organos", joinIds(f.getOrganos()));
        }
        if (notEmpty(f.getInstrumentos())) {
            b.queryParam("instrumentos", joinIds(f.getInstrumentos()));
        }
        if (f.getFinalidad() != null) {
            b.queryParam("finalidad", f.getFinalidad());
        }
        if (notEmpty(f.getTiposBeneficiario())) {
            b.queryParam("tiposBeneficiario", joinIds(f.getTiposBeneficiario()));
        }
        if (hasText(f.getOrder())) {
            b.queryParam("order", f.getOrder());
        }
        if (hasText(f.getDireccion())) {
            b.queryParam("direccion", f.getDireccion());
        }

        String url = b.toUriString();
        log.debug("[FILTROS] URL: {}", url);
        return url;
    }

    private String inferirTipoAdmon(String ubicacion, List<Integer> regionIds) {
        if (ubicacion == null || ubicacion.isBlank()) {
            return null;
        }
        String u = ubicacion.toLowerCase();
        if (u.contains("nacional") || u.contains("estado")) {
            return "C";
        }
        if (!regionIds.isEmpty()) {
            return "A";
        }
        return null;
    }

    private String joinIds(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private boolean notEmpty(List<?> l) {
        return l != null && !l.isEmpty();
    }

    private String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static String resolverDescripcionLegacy(Proyecto proyecto, Perfil perfil) {
        String sector = proyecto.getSector();
        if (sector != null && !sector.isBlank()) {
            String normalizado = SectorNormalizador.normalizarABusqueda(sector);
            if (normalizado != null) {
                return normalizado;
            }
        }

        if (perfil != null && perfil.getSector() != null && !perfil.getSector().isBlank()) {
            String normalizado = SectorNormalizador.normalizarABusqueda(perfil.getSector());
            if (normalizado != null) {
                return normalizado;
            }
        }

        if (proyecto.getNombre() != null && !proyecto.getNombre().isBlank()) {
            String nombre = proyecto.getNombre().toLowerCase().trim();
            if (nombre.length() > 50) {
                nombre = nombre.substring(0, 50);
            }
            return "subvencion " + nombre;
        }

        return "subvencion pyme empresa";
    }

    private static String resolverUbicacionLegacy(Proyecto proyecto, Perfil perfil) {
        if (proyecto.getUbicacion() != null && !proyecto.getUbicacion().isBlank()) {
            return proyecto.getUbicacion();
        }
        if (perfil != null && perfil.getUbicacion() != null && !perfil.getUbicacion().isBlank()) {
            return perfil.getUbicacion();
        }
        return null;
    }
}
