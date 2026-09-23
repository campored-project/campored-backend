package com.campored.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroProductorRequest {

    private static final String PATRON_TELEFONO = "^\\+?[0-9]{10,13}$";

    @Schema(example = "juan@finca.com")
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 150, message = "El correo no puede superar 150 caracteres")
    private String correo;

    @Schema(example = "SecurePass123")
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 64, message = "La contraseña debe tener entre 8 y 64 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "La contraseña debe incluir mayúsculas, minúsculas y números")
    private String contrasena;

    @Schema(example = "Juan Pérez")
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
    private String nombre;

    @Schema(example = "Finca La Esperanza")
    @NotBlank(message = "El nombre de la finca es obligatorio")
    @Size(max = 120, message = "El nombre de la finca no puede superar 120 caracteres")
    private String nombreFinca;

    @Schema(example = "El Sauce")
    @NotBlank(message = "La vereda es obligatoria")
    @Size(max = 120, message = "La vereda no puede superar 120 caracteres")
    private String vereda;

    @Schema(example = "SONSON")
    @NotBlank(message = "El municipio es obligatorio")
    private String municipio;

    @Schema(example = "+573001234567")
    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = PATRON_TELEFONO, message = "El teléfono debe tener entre 10 y 13 dígitos")
    private String telefono;

    @Schema(example = "+573001234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El WhatsApp debe tener entre 10 y 13 dígitos")
    private String whatsapp;
}
