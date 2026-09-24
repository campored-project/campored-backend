// ============================================================
// FEATURE: US-05 — Gestión de Perfil del Comprador Comercial (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Actualización de dirección, municipio y datos de entrega del negocio
// ============================================================
package com.campored.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Solo se modifican los campos enviados; en los opcionales, una cadena vacía borra el valor")
public class ActualizarPerfilCompradorRequest {

    public static final String MENSAJE_DIRECCION_VACIA = "La dirección no puede quedar vacía";

    private static final String PATRON_TELEFONO = "^(\\+?[0-9]{10,13})?$";

    // null se acepta porque el campo no cambia; vacío o solo espacios no
    private static final String PATRON_NO_VACIO = "(?s).*\\S.*";

    @Schema(example = "Carrera 50 # 45-10, local 2")
    @Pattern(regexp = PATRON_NO_VACIO, message = MENSAJE_DIRECCION_VACIA)
    @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
    private String direccion;

    @Schema(example = "LA_CEJA")
    @Pattern(regexp = PATRON_NO_VACIO, message = "El municipio no puede quedar vacío")
    private String municipio;

    @Schema(example = "Lunes a viernes, 5:00 a 9:00 a. m.")
    @Size(max = 120, message = "El horario de recepción no puede superar 120 caracteres")
    private String horarioRecepcion;

    @Schema(example = "Timbre junto a la puerta de carga")
    @Size(max = 500, message = "Las notas de acceso no pueden superar 500 caracteres")
    private String notasAcceso;

    @Schema(example = "+573001234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El teléfono debe tener entre 10 y 13 dígitos")
    private String telefono;

    @Schema(example = "+573001234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El WhatsApp debe tener entre 10 y 13 dígitos")
    private String whatsapp;
}
