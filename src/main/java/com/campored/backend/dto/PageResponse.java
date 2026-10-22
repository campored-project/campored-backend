// ============================================================
// FEATURE: US-11 — Catálogo por Municipio y Disponibilidad (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Contrato de paginación estable, independiente de la serialización de PageImpl
// ============================================================
package com.campored.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Página de resultados")
public record PageResponse<T>(
        List<T> content,
        @Schema(description = "Número de página, empieza en 0") int number,
        @Schema(description = "Tamaño de página aplicado") int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty());
    }
}
