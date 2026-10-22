// ============================================================
// FEATURE: US-03 — Registro de Comprador Comercial (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Registro de negocios compradores con JWT
// ============================================================
package com.campored.backend.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TipoNegocio {
    RESTAURANTE,
    TIENDA,
    MINIMERCADO,
    MAYORISTA;

    public static Optional<TipoNegocio> desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String codigo = valor.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(tipo -> tipo.name().equals(codigo))
                .findFirst();
    }
}
