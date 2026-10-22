// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.util;

import com.campored.backend.dto.OfertaResponse;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Oferta;

public final class OfertaMapper {

    private OfertaMapper() {
    }

    public static OfertaResponse toResponse(Oferta oferta) {
        Finca finca = oferta.getFinca();
        return OfertaResponse.builder()
                .id(oferta.getId())
                .producto(oferta.getProducto())
                .productoOtro(oferta.getProductoOtro())
                .descripcion(oferta.getDescripcion())
                .cantidadDisponible(oferta.getCantidadDisponible())
                .unidadMedida(oferta.getUnidadMedida())
                .precioUnitario(oferta.getPrecioUnitario())
                .estadoCultivo(oferta.getEstadoCultivo())
                .fechaEstimadaCosecha(oferta.getFechaEstimadaCosecha())
                .modalidadLogistica(oferta.getModalidadLogistica())
                .activa(oferta.isActiva())
                .fechaCreacion(oferta.getFechaCreacion())
                .fincaId(finca.getId())
                .fincaNombre(finca.getNombreFinca())
                .municipio(finca.getMunicipio())
                .build();
    }
}
