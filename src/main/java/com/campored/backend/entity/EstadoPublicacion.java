// ============================================================
// FEATURE: US-09 — Marcar Oferta como Agotada (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Estado visible de una oferta, calculado a partir de la columna activa
// ============================================================
package com.campored.backend.entity;

// No se persiste: activa=true es ACTIVA y activa=false es AGOTADA (borrado lógico)
public enum EstadoPublicacion {
    ACTIVA,
    AGOTADA
}
