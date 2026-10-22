// ============================================================
// FEATURE: US-09 — Marcar Oferta como Agotada (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Borrado lógico de una oferta propia para retirarla del catálogo
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.EstadoPublicacion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solo se admite AGOTADA: una oferta agotada no se puede reactivar")
public class CambiarEstadoOfertaRequest {

    @Schema(example = "AGOTADA")
    @NotNull(message = "El estado de publicación es obligatorio")
    private EstadoPublicacion estadoPublicacion;
}
