// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
// ============================================================
package com.campored.backend.entity;

// El MVP no cambia el estado del pedido: aceptar, entregar y cancelar quedan fuera de alcance
public enum EstadoPedido {
    SOLICITADO
}
