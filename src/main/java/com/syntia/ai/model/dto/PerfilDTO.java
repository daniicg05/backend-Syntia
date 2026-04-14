package com.syntia.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PerfilDTO {
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotBlank(message = "El sector es obligatorio")
    private String sector;

    @NotBlank(message = "La ubicación es obligatoria")
    private String ubicacion;

    @Size(max = 255, message = "La empresa no puede superar los 255 caracteres")
    private String empresa;

    @Size(max = 100, message = "La provincia no puede superar los 100 caracteres")
    private String provincia;

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String telefono;

    private String tipoEntidad;

    @Size(max = 500, message = "Los objetivos no pueden superar los 500 caracteres")
    private String objetivos;

    @Size(max = 500, message = "Las necesidades de financiación no pueden superar los 500 caracteres")
    private String necesidadesFinanciacion;

    @Size(max = 2000, message = "La descripción libre no puede superar los 2000 caracteres")
    private String descripcionLibre;
}
