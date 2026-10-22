package com.campored.backend.util;

import com.campored.backend.entity.Producto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// ============================================================
// FEATURE: US-13 — Filtros por Producto y Modalidad (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Nombres equivalentes de un producto para reconocer ofertas OTRO
// ============================================================
@DisplayName("ProductoNormalizer - Nombres equivalentes de un producto")
class ProductoNormalizerTest {

    @Test
    @DisplayName("Debe incluir la etiqueta y el código del enum en minúsculas")
    void testCandidatosMoraCastilla() {
        assertEquals(List.of("mora de castilla", "mora castilla"),
                List.copyOf(ProductoNormalizer.candidatos(Producto.MORA_CASTILLA)));
    }

    @Test
    @DisplayName("Debe incluir la etiqueta con y sin tildes sin repetir valores")
    void testCandidatosConTilde() {
        Set<String> candidatos = ProductoNormalizer.candidatos(Producto.CAFE_PERGAMINO);

        assertEquals(List.of("café pergamino", "cafe pergamino"), List.copyOf(candidatos));
    }

    @Test
    @DisplayName("Debe quitar tildes y diéresis sin alterar el resto del texto")
    void testSinTildes() {
        assertEquals("Cafe pergamino", ProductoNormalizer.sinTildes("Café pergamino"));
        assertEquals("Platano", ProductoNormalizer.sinTildes("Plátano"));
        assertEquals("pinguino", ProductoNormalizer.sinTildes("pingüino"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Producto.class)
    @DisplayName("Debe generar candidatos en minúsculas y sin espacios sobrantes para cada producto")
    void testCandidatosNormalizados(Producto producto) {
        Set<String> candidatos = ProductoNormalizer.candidatos(producto);

        assertFalse(candidatos.isEmpty());
        candidatos.forEach(candidato -> {
            assertEquals(candidato.trim(), candidato);
            assertEquals(candidato.toLowerCase(), candidato);
            assertFalse(candidato.contains("_"), candidato);
        });
    }
}
