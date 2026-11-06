// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Registro de cada contacto del comprador con un productor por WhatsApp o llamada
// ============================================================
package com.campored.backend.util;

import com.campored.backend.dto.ContactoResponse;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Pedido;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.RegistroContacto;

// Las relaciones del registro son LAZY: se mapea dentro de la transacción del servicio
public final class ContactoMapper {

    private ContactoMapper() {
    }

    public static ContactoResponse toResponse(RegistroContacto registro) {
        Oferta oferta = registro.getOferta();
        Pedido pedido = registro.getPedido();
        return new ContactoResponse(
                registro.getId(),
                registro.getFechaContacto(),
                registro.getCanal(),
                oferta.getId(),
                pedido == null ? null : pedido.getId(),
                nombreProducto(oferta),
                oferta.getProductor().getNombre(),
                oferta.getFinca().getNombreFinca());
    }

    private static String nombreProducto(Oferta oferta) {
        return oferta.getProducto() == Producto.OTRO ? oferta.getProductoOtro() : oferta.getProducto().getEtiqueta();
    }
}
