// ============================================================
// FEATURE: US-11 / US-13 — Catálogo Público de Ofertas (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Consulta paginada de ofertas activas con filtros combinables
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.Municipio;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Datos de la finca visibles en el catálogo público")
public record FincaPublicaResponse(
        UUID id,
        @Schema(example = "Finca El Roble") String nombre,
        @Schema(example = "La Honda") String vereda,
        @Schema(example = "SONSON") Municipio municipio) {
}
