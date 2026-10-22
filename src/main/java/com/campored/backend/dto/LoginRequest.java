// ============================================================
// FEATURE: US-02 — Login de Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Inicio de sesión con correo y contraseña, emite JWT
// ============================================================
package com.campored.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @Schema(example = "juan@finca.com")
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    private String correo;

    @Schema(example = "SecurePass123")
    @NotBlank(message = "La contraseña es obligatoria")
    private String contrasena;
}
