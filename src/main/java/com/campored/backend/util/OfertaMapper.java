// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.util;

import com.campored.backend.dto.FincaPublicaResponse;
import com.campored.backend.dto.OfertaCatalogoResponse;
import com.campored.backend.dto.OfertaDetalleResponse;
import com.campored.backend.dto.OfertaResponse;
import com.campored.backend.dto.ProductorPublicoResponse;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.EstadoPublicacion;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Usuario;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
                .estadoPublicacion(estadoPublicacion(oferta))
                .fechaCreacion(oferta.getFechaCreacion())
                .fincaId(finca.getId())
                .fincaNombre(finca.getNombreFinca())
                .municipio(finca.getMunicipio())
                .build();
    }

    // ============================================================
    // FEATURE: US-11 / US-13 — Catálogo Público de Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta paginada de ofertas activas con filtros combinables
    // ============================================================
    // "hoy" llega desde el servicio para que toda la página use la misma fecha de Colombia
    public static OfertaCatalogoResponse toCatalogoResponse(Oferta oferta, LocalDate hoy) {
        return new OfertaCatalogoResponse(
                oferta.getId(),
                oferta.getProducto(),
                oferta.getProductoOtro(),
                oferta.getCantidadDisponible(),
                oferta.getUnidadMedida(),
                oferta.getPrecioUnitario(),
                oferta.getEstadoCultivo(),
                oferta.getFechaEstimadaCosecha(),
                diasRestantesCosecha(oferta, hoy),
                oferta.getModalidadLogistica(),
                oferta.getFechaCreacion(),
                toFincaPublica(oferta.getFinca()));
    }

    // ============================================================
    // FEATURE: US-12 — Detalle de Oferta (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta pública de una oferta activa con su finca y productor
    // ============================================================
    public static OfertaDetalleResponse toDetalleResponse(Oferta oferta, LocalDate hoy, boolean incluirContacto) {
        return new OfertaDetalleResponse(
                oferta.getId(),
                oferta.getProducto(),
                oferta.getProductoOtro(),
                oferta.getDescripcion(),
                oferta.getCantidadDisponible(),
                oferta.getUnidadMedida(),
                oferta.getPrecioUnitario(),
                oferta.getEstadoCultivo(),
                oferta.getFechaEstimadaCosecha(),
                diasRestantesCosecha(oferta, hoy),
                oferta.getModalidadLogistica(),
                oferta.getFechaCreacion(),
                estadoPublicacion(oferta),
                toFincaPublica(oferta.getFinca()),
                toProductorPublico(oferta.getProductor(), incluirContacto));
    }

    // ============================================================
    // FEATURE: US-09 — Marcar Oferta como Agotada (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Estado visible de una oferta, calculado a partir de la columna activa
    // ============================================================
    // Hoy la única forma de desactivar una oferta es marcarla como agotada (US-09)
    public static EstadoPublicacion estadoPublicacion(Oferta oferta) {
        return oferta.isActiva() ? EstadoPublicacion.ACTIVA : EstadoPublicacion.AGOTADA;
    }

    // Un canal apagado por el productor no expone su número aunque quien consulta sea comprador
    private static ProductorPublicoResponse toProductorPublico(Usuario productor, boolean incluirContacto) {
        boolean whatsappDisponible = productor.isCanalWhatsappHabilitado() && tieneTexto(productor.getWhatsapp());
        boolean llamadaDisponible = productor.isCanalLlamadaHabilitado() && tieneTexto(productor.getTelefono());
        return new ProductorPublicoResponse(
                productor.getId(),
                productor.getNombre(),
                whatsappDisponible,
                llamadaDisponible,
                incluirContacto && llamadaDisponible ? productor.getTelefono() : null,
                incluirContacto && whatsappDisponible ? productor.getWhatsapp() : null);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    // Una cosecha vencida que el productor no ha actualizado se muestra como 0, nunca negativa
    public static Integer diasRestantesCosecha(Oferta oferta, LocalDate hoy) {
        LocalDate fechaCosecha = oferta.getFechaEstimadaCosecha();
        if (oferta.getEstadoCultivo() == EstadoCultivo.DISPONIBLE || fechaCosecha == null) {
            return null;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(hoy, fechaCosecha));
    }

    private static FincaPublicaResponse toFincaPublica(Finca finca) {
        return new FincaPublicaResponse(finca.getId(), finca.getNombreFinca(), finca.getVereda(), finca.getMunicipio());
    }
}
