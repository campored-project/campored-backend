// ============================================================
// FEATURE: US-04 — Gestión de Perfil del Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Actualización de teléfono, WhatsApp y canales de contacto
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.dto.validation.CanalesContactoValidos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CanalesContactoValidos
@Schema(description = "Solo se modifican los campos enviados; una cadena vacía borra el número guardado")
public class ActualizarPerfilProductorRequest {

    public static final String MENSAJE_TELEFONO_REQUERIDO =
            "El teléfono es obligatorio para habilitar el canal de llamadas";
    public static final String MENSAJE_WHATSAPP_REQUERIDO =
            "El WhatsApp es obligatorio para habilitar el canal de WhatsApp";

    // A diferencia del registro, se acepta la cadena vacía para poder borrar el número
    private static final String PATRON_TELEFONO = "^(\\+?[0-9]{10,13})?$";

    @Schema(example = "+573001234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El teléfono debe tener entre 10 y 13 dígitos")
    private String telefono;

    @Schema(example = "+573001234567")
    @Pattern(regexp = PATRON_TELEFONO, message = "El WhatsApp debe tener entre 10 y 13 dígitos")
    private String whatsapp;

    @Schema(example = "true")
    private Boolean canalWhatsappHabilitado;

    @Schema(example = "true")
    private Boolean canalLlamadaHabilitado;
}
