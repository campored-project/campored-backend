// ============================================================
// FEATURE: US-12 — Detalle de Oferta (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Consulta pública de una oferta activa con su finca y productor
// ============================================================
package com.campored.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// Endpoint anónimo: correo, teléfono, WhatsApp y rol del productor no se exponen aquí
@Schema(description = "Datos públicos del productor que publica la oferta")
public record ProductorPublicoResponse(
        UUID id,
        @Schema(example = "María Gómez") String nombreCompleto) {
}
