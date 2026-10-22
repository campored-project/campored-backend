// ============================================================
// FEATURE: US-11 / US-13 — Catálogo Público de Ofertas (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Consulta paginada de ofertas activas con filtros combinables
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Producto;

// Un filtro null no restringe la consulta
public record FiltroCatalogoOfertas(
        Municipio municipio,
        EstadoCultivo estadoCultivo,
        Producto producto,
        ModalidadLogistica modalidadLogistica) {
}
