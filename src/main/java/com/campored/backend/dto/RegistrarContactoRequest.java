// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Registro de cada contacto del comprador con un productor por WhatsApp o llamada
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.CanalContacto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "El comprador se toma del token. La fecha del contacto la fija el backend")
public class RegistrarContactoRequest {

    public static final String MENSAJE_OFERTA_REQUERIDA = "Debes indicar la oferta contactada.";
    public static final String MENSAJE_CANAL_REQUERIDO = "Debes indicar el canal de contacto.";

    @Schema(example = "8f2d6c1e-4b7a-4f3e-9c2d-1a2b3c4d5e6f")
    @NotNull(message = MENSAJE_OFERTA_REQUERIDA)
    private UUID ofertaId;

    @Schema(example = "WHATSAPP")
    @NotNull(message = MENSAJE_CANAL_REQUERIDO)
    private CanalContacto canal;

    @Schema(example = "3c9a7e10-2f4b-4c1d-8e6a-5b7c9d0e1f23", nullable = true,
            description = "Pedido desde el que se contacta; debe ser del comprador y de la misma oferta")
    private UUID pedidoId;
}
