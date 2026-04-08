package com.syntia.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ConvocatoriaDTO {
    @NotBlank(message = "El título es obligatorio")
    @Size(max = 300, message = "El título no puede superar los 300 caracteres")
    private String titulo;

    private String tipo;
    private String sector;
    private String ubicacion;

    @Size(max = 500, message = "La URL no puede superar los 500 caracteres")
    private String urlOficial;

    private String fuente;

    private String idBdns;

    private String numeroConvocatoria;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaCierre;

    private String organismo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaPublicacion;

    private String descripcion;

    private String textoCompleto;

    public ConvocatoriaDTO() {
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getUrlOficial() {
        return urlOficial;
    }

    public void setUrlOficial(String urlOficial) {
        this.urlOficial = urlOficial;
    }

    public String getFuente() {
        return fuente;
    }

    public void setFuente(String fuente) {
        this.fuente = fuente;
    }

    public String getIdBdns() {
        return idBdns;
    }

    public void setIdBdns(String idBdns) {
        this.idBdns = idBdns;
    }

    public String getNumeroConvocatoria() {
        return numeroConvocatoria;
    }

    public void setNumeroConvocatoria(String numeroConvocatoria) {
        this.numeroConvocatoria = numeroConvocatoria;
    }

    public LocalDate getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDate fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public String getOrganismo() {
        return organismo;
    }

    public void setOrganismo(String organismo) {
        this.organismo = organismo;
    }

    public LocalDate getFechaPublicacion() {
        return fechaPublicacion;
    }

    public void setFechaPublicacion(LocalDate fechaPublicacion) {
        this.fechaPublicacion = fechaPublicacion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTextoCompleto() {
        return textoCompleto;
    }

    public void setTextoCompleto(String textoCompleto) {
        this.textoCompleto = textoCompleto;
    }
}
