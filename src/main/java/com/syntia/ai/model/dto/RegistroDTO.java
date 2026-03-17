package com.syntia.ai.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO (Data Transfer Object) para el registro de usuarios.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 * <li>Transportar los datos del formulario/API de registro entre capas.</li>
 * <li>Definir reglas de validación con Bean Validation (Jakarta Validation).</li>
 * <li>Evitar exponer entidades de persistencia directamente en la capa web.</li>
 * </ul>
 *
 * <p>Notas de diseño:</p>
 * <ul>
 * <li>No contiene lógica de negocio, solo estructura y restricciones de entrada.</li>
 * <li>Las anotaciones de Lombok generan automáticamente getters y setters.</li>
 * <li>La comparación entre {@code password} y {@code confirmarPassword}
 * normalmente se realiza en una validación adicional (servicio o validador custom).</li>
 * </ul>
 */
@Getter
@Setter
public class RegistroDTO {

    /**
     * Correo electrónico del usuario.
     *
     * <p>Validaciones aplicadas:</p>
     * <ul>
     * <li>{@link NotBlank}: no permite null, vacío ni solo espacios.</li>
     * <li>{@link Email}: exige formato de correo válido (ej.: usuario@dominio.com).</li>
     * </ul>
     */
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe tener un formato válido")
    private String email;

    /**
     * Contraseña principal del usuario.
     *
     * <p>Validaciones aplicadas:</p>
     * <ul>
     * <li>{@link NotBlank}: obliga a informar un valor.</li>
     * <li>{@link Size}: requiere una longitud mínima de4 caracteres.</li>
     * </ul>
     *
     * <p>Importante: este DTO transporta la contraseña en texto plano temporalmente * durante la solicitud. Debe cifrarse antes de persistirla.</p>
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 4, message = "La contraseña debe tener al menos 4 caracteres")
    private String password;

    /**
     * Campo de confirmación de contraseña.
     *
     * <p>Validación aplicada:</p>
     * <ul>
     * <li>{@link NotBlank}: obliga a confirmar la contraseña.</li>
     * </ul>
     *
     * <p>Nota: para asegurar que coincida con {@code password}, se recomienda * una validación cruzada a nivel de clase o en la capa de servicio.</p>
     */
    @NotBlank(message = "Debes confirmar la contraseña")
    private String confirmarPassword;
}