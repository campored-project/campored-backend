// ============================================================
// FEATURE: US-13 — Filtros por Producto y Modalidad (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Nombre visible usado para reconocer ofertas OTRO que describen un producto del catálogo
// ============================================================
package com.campored.backend.util;

import com.campored.backend.entity.Producto;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

// La comparación sin tildes se resuelve aquí y no con unaccent(): H2 no lo soporta y PostgreSQL
// lo exige como extensión. La consulta solo aplica lower(trim(...)) sobre producto_otro
public final class ProductoNormalizer {

    private ProductoNormalizer() {
    }

    public static Set<String> candidatos(Producto producto) {
        String etiqueta = producto.getEtiqueta().trim().toLowerCase(Locale.ROOT);
        Set<String> candidatos = new LinkedHashSet<>();
        candidatos.add(etiqueta);
        candidatos.add(sinTildes(etiqueta));
        candidatos.add(producto.name().replace('_', ' ').toLowerCase(Locale.ROOT));
        return Collections.unmodifiableSet(candidatos);
    }

    public static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
