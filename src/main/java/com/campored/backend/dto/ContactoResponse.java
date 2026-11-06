// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Registro de cada contacto del comprador con un productor por WhatsApp o llamada
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.CanalContacto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

// Sin números de contacto: el historial solo recuerda a quién se contactó y por qué canal
@Schema(description = "Contacto registrado por el comprador")
public record ContactoResponse(
        UUID id,
        LocalDateTime fechaContacto,
        @Schema(example = "WHATSAPP") CanalContacto canal,
        UUID ofertaId,
        @Schema(nullable = true) UUID pedidoId,
        @Schema(description = "Nombre visible del producto; si la oferta es OTRO, el texto de productoOtro",
                example = "Tomate chonto")
        String producto,
        @Schema(example = "María Gómez") String productorNombre,
        @Schema(example = "Finca El Roble") String fincaNombre) {
}
