// ============================================================
// FEATURE: US-03 — Registro de Comprador Comercial (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Registro de negocios compradores con JWT
// ============================================================
package com.campored.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroCompradorRequest {

    private static final String PATRON_TELEFONO = "^\\+?[0-9]{10,13}$";

    @Schema(example = "compras@restauranteelfogon.com")
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

    @Schema(example = "Laura Restrepo")
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    private String nombre;

    @Schema(example = "Restaurante El Fogón")
    @NotBlank(message = "El nombre del negocio es obligatorio")
    @Size(max = 120, message = "El nombre del negocio no puede superar 120 caracteres")
    private String nombreNegocio;

    @Schema(example = "RESTAURANTE", allowableValues = {"RESTAURANTE", "TIENDA", "MINIMERCADO", "MAYORISTA"})
    @NotBlank(message = "El tipo de negocio es obligatorio")
    @Pattern(
            regexp = "(?i)(RESTAURANTE|TIENDA|MINIMERCADO|MAYORISTA)",
            message = "El tipo de negocio debe ser RESTAURANTE, TIENDA, MINIMERCADO o MAYORISTA")
    private String tipoNegocio;

    @Schema(example = "Calle 49 # 50-21, local 3")
    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
    private String direccion;

    @Schema(example = "RIONEGRO")
    @NotBlank(message = "El municipio es obligatorio")
    private String municipio;

    @Schema(example = "Lunes a sábado, 6:00 a 10:00 a. m.")
    @Size(max = 120, message = "El horario de recepción no puede superar 120 caracteres")
    private String horarioRecepcion;

    @Schema(example = "Entrada de proveedores por la parte trasera")
    @Size(max = 500, message = "Las notas de acceso no pueden superar 500 caracteres")
    private String notasAcceso;

    @Schema(example = "+573001234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El teléfono debe tener entre 10 y 13 dígitos")
    private String telefono;

    @Schema(example = "+573001234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El WhatsApp debe tener entre 10 y 13 dígitos")
    private String whatsapp;
}
