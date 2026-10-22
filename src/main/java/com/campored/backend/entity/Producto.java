// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.entity;

// OTRO exige describir el producto en Oferta.productoOtro
public enum Producto {
    TOMATE_CHONTO("Tomate chonto"),
    CAFE_PERGAMINO("Café pergamino"),
    MORA_CASTILLA("Mora de castilla"),
    CILANTRO("Cilantro"),
    FRIJOL_CARGAMANTO("Fríjol cargamanto"),
    AGUACATE("Aguacate"),
    PLATANO("Plátano"),
    OTRO("Otro");

    // ============================================================
    // FEATURE: US-13 — Filtros por Producto y Modalidad (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Nombre visible usado para reconocer ofertas OTRO que describen un producto del catálogo
    // ============================================================
    private final String etiqueta;

    Producto(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
