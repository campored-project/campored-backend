// ============================================================
// FEATURE: US-12 — Detalle de Oferta (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Consulta pública de una oferta activa con su finca y productor
// ============================================================
package com.campored.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// Endpoint anónimo: correo y rol nunca se exponen; los números solo llegan a compradores autenticados
@Schema(description = "Datos públicos del productor que publica la oferta")
public record ProductorPublicoResponse(
        UUID id,
        @Schema(example = "María Gómez") String nombreCompleto,
        @Schema(description = "El productor habilitó WhatsApp y tiene un número de WhatsApp registrado")
        boolean whatsappDisponible,
        @Schema(description = "El productor habilitó llamadas y tiene un teléfono registrado")
        boolean llamadaDisponible,
        @Schema(description = "Teléfono para llamar. Solo se envía a compradores autenticados (ROLE_COMPRADOR) "
                + "y si llamadaDisponible es true; en otro caso es null", example = "+573001234567", nullable = true)
        String telefonoContacto,
        @Schema(description = "Número de WhatsApp. Solo se envía a compradores autenticados (ROLE_COMPRADOR) "
                + "y si whatsappDisponible es true; en otro caso es null", example = "+573001234567", nullable = true)
        String whatsappContacto) {
}
