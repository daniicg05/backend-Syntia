package com.syntia.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltrosBdns {

    // PAGINACION Y ORDEN
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer pageSize = 15;

    @Builder.Default
    private String vpd = "GE";

    private String order;
    private String direccion;

    // TEXTO LIBRE
    private String descripcion;
    private Integer descripcionTipoBusqueda;
    private String numeroConvocatoria;
    private String ayudaEstado;

    // FLAGS
    private Boolean mrr;
    private Boolean contribucion;

    // RANGO TEMPORAL
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    // TIPO ADMINISTRACION
    private String tipoAdministracion;

    // IDs DE CATALOGO
    private List<Integer> regiones;
    private List<Integer> organos;
    private List<Integer> instrumentos;
    private Integer finalidad;
    private List<Integer> tiposBeneficiario;

    // Compatibilidad con llamadas existentes que usan estilo record
    public String descripcion() {
        return descripcion;
    }

    public Integer regionId() {
        return (regiones == null || regiones.isEmpty()) ? null : regiones.get(0);
    }

    public Integer finalidadId() {
        return finalidad;
    }

    public boolean tieneAlgunFiltro() {
        return (descripcion != null && !descripcion.isBlank()) || regionId() != null || finalidad != null;
    }

    public FiltrosBdns sinDescripcion() {
        return FiltrosBdns.builder()
                .page(page)
                .pageSize(pageSize)
                .vpd(vpd)
                .order(order)
                .direccion(direccion)
                .descripcion(null)
                .descripcionTipoBusqueda(descripcionTipoBusqueda)
                .numeroConvocatoria(numeroConvocatoria)
                .ayudaEstado(ayudaEstado)
                .mrr(mrr)
                .contribucion(contribucion)
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .tipoAdministracion(tipoAdministracion)
                .regiones(regiones)
                .organos(organos)
                .instrumentos(instrumentos)
                .finalidad(finalidad)
                .tiposBeneficiario(tiposBeneficiario)
                .build();
    }

    public FiltrosBdns sinTerritorio() {
        return FiltrosBdns.builder()
                .page(page)
                .pageSize(pageSize)
                .vpd(vpd)
                .order(order)
                .direccion(direccion)
                .descripcion(descripcion)
                .descripcionTipoBusqueda(descripcionTipoBusqueda)
                .numeroConvocatoria(numeroConvocatoria)
                .ayudaEstado(ayudaEstado)
                .mrr(mrr)
                .contribucion(contribucion)
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .tipoAdministracion(tipoAdministracion)
                .regiones(null)
                .organos(organos)
                .instrumentos(instrumentos)
                .finalidad(finalidad)
                .tiposBeneficiario(tiposBeneficiario)
                .build();
    }
}
