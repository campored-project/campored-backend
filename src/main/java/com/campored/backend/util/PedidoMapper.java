// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
// ============================================================
package com.campored.backend.util;

import com.campored.backend.dto.PedidoResponse;
import com.campored.backend.dto.ProductorPublicoResponse;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Negocio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Pedido;
import com.campored.backend.entity.Usuario;

// Las relaciones del pedido son LAZY: se mapea dentro de la transacción del servicio
public final class PedidoMapper {

    private PedidoMapper() {
    }

    public static PedidoResponse toResponse(Pedido pedido) {
        return new PedidoResponse(
                pedido.getId(),
                pedido.getEstado(),
                pedido.getFechaCreacion(),
                pedido.getCantidadSolicitada(),
                pedido.getPrecioUnitario(),
                pedido.getPrecioTotalEstimado(),
                pedido.getDireccionEntrega(),
                pedido.getMunicipioEntrega(),
                pedido.getFechaPreferida(),
                pedido.getJornada(),
                pedido.getModalidadEntrega(),
                pedido.getNotasAcceso(),
                toOferta(pedido.getOferta()),
                toProductor(pedido.getOferta().getProductor()),
                toComprador(pedido.getComprador()));
    }

    private static PedidoResponse.OfertaPedido toOferta(Oferta oferta) {
        Finca finca = oferta.getFinca();
        return new PedidoResponse.OfertaPedido(
                oferta.getId(),
                oferta.getProducto(),
                oferta.getProductoOtro(),
                oferta.getUnidadMedida(),
                finca.getNombreFinca(),
                finca.getMunicipio());
    }

    // Misma regla de canales que el detalle de oferta (US-12); quien ve un pedido siempre es una de sus partes
    private static PedidoResponse.ProductorPedido toProductor(Usuario productor) {
        ProductorPublicoResponse publico = OfertaMapper.toProductorPublico(productor, true);
        return new PedidoResponse.ProductorPedido(
                publico.nombreCompleto(),
                publico.whatsappDisponible(),
                publico.llamadaDisponible(),
                publico.whatsappContacto(),
                publico.telefonoContacto());
    }

    private static PedidoResponse.CompradorPedido toComprador(Usuario comprador) {
        Negocio negocio = comprador.getNegocio();
        return new PedidoResponse.CompradorPedido(
                comprador.getNombre(),
                negocio == null ? null : negocio.getNombreNegocio(),
                comprador.getWhatsapp(),
                comprador.getTelefono());
    }
}
