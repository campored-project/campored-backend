// ============================================================
// FEATURE: US-08 — Detalle de Oferta (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Consulta pública de una oferta activa con su finca y productor
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.UnidadMedida;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Detalle público de una oferta activa")
public record OfertaDetalleResponse(
        UUID id,
        Producto producto,
        String productoOtro,
        String descripcion,
        BigDecimal cantidadDisponible,
        UnidadMedida unidadMedida,
        BigDecimal precioUnitario,
        EstadoCultivo estadoCultivo,
        LocalDate fechaEstimadaCosecha,
        @Schema(description = "Días hasta la cosecha según la fecha de Colombia; 0 si ya pasó, null si el producto está DISPONIBLE",
                example = "10", nullable = true)
        Integer diasRestantesCosecha,
        ModalidadLogistica modalidadLogistica,
        @Schema(description = "Fecha de publicación de la oferta")
        LocalDateTime fechaCreacion,
        FincaPublicaResponse finca,
        ProductorPublicoResponse productor) {
}
