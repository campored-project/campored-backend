// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.EstadoPedido;
import com.campored.backend.entity.Jornada;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.UnidadMedida;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Solo lo ven el comprador dueño y el productor de la oferta; por eso viajan los números de ambos
@Schema(description = "Pedido con los datos de entrega y el contacto de las dos partes")
public record PedidoResponse(
        UUID id,
        @Schema(example = "SOLICITADO") EstadoPedido estado,
        @Schema(description = "Fecha y hora de creación en hora de Colombia", example = "2026-11-05T20:28:07.616432-05:00")
        OffsetDateTime fechaCreacion,
        @Schema(example = "120.00") BigDecimal cantidadSolicitada,
        @Schema(description = "Precio de la oferta en el momento del pedido", example = "3500.00")
        BigDecimal precioUnitario,
        @Schema(description = "cantidadSolicitada × precioUnitario, redondeado a 2 decimales", example = "420000.00")
        BigDecimal precioTotalEstimado,
        @Schema(example = "Calle 49 # 50-21, local 3") String direccionEntrega,
        @Schema(example = "RIONEGRO") Municipio municipioEntrega,
        LocalDate fechaPreferida,
        @Schema(example = "MANANA") Jornada jornada,
        @Schema(description = "RECOGIDA_FINCA o DESPACHO_PRODUCTOR; nunca AMBAS", example = "DESPACHO_PRODUCTOR")
        ModalidadLogistica modalidadEntrega,
        @Schema(nullable = true) String notasAcceso,
        OfertaPedido oferta,
        ProductorPedido productor,
        CompradorPedido comprador) {

    @Schema(name = "OfertaPedidoResponse", description = "Resumen de la oferta pedida")
    public record OfertaPedido(
            UUID id,
            @Schema(example = "TOMATE_CHONTO") Producto producto,
            @Schema(nullable = true) String productoOtro,
            @Schema(example = "KILOGRAMO") UnidadMedida unidadMedida,
            @Schema(example = "Finca El Roble") String fincaNombre,
            @Schema(example = "SONSON") Municipio municipio) {
    }

    @Schema(name = "ProductorPedidoResponse",
            description = "Contacto del productor. Los números solo llegan si el canal está habilitado y tiene número")
    public record ProductorPedido(
            @Schema(example = "María Gómez") String nombreCompleto,
            boolean whatsappDisponible,
            boolean llamadaDisponible,
            @Schema(example = "+573109876543", nullable = true) String whatsappContacto,
            @Schema(example = "+573001234567", nullable = true) String telefonoContacto) {
    }

    @Schema(name = "CompradorPedidoResponse", description = "Contacto del comprador que hizo el pedido")
    public record CompradorPedido(
            @Schema(example = "Laura Restrepo") String nombre,
            @Schema(example = "Restaurante El Fogón", nullable = true) String nombreNegocio,
            @Schema(example = "+573201112233", nullable = true) String whatsapp,
            @Schema(example = "+576045551234", nullable = true) String telefono) {
    }
}
