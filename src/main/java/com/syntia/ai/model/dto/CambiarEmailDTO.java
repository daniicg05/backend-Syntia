package com.syntia.ai.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarEmailDTO {

    @NotBlank(message = "El nuevo email es obligatorio")
    @Email(message = "El nuevo email no tiene un formato válido")
    private String nuevoEmail;

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;
}
