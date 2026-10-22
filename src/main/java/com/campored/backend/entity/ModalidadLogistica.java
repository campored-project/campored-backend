// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.entity;

// AMBAS deja que el comprador elija entre recogida y despacho en el checkout
public enum ModalidadLogistica {
    RECOGIDA_FINCA,
    DESPACHO_PRODUCTOR,
    AMBAS
}
