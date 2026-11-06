// ============================================================
// FEATURE: US-22 — Manejo de Errores y Formato de Respuestas (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-06
// Descripción: Conversión de las fechas del API a hora de Colombia con offset
// ============================================================
package com.campored.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("FechaRespuesta - fechas del API en hora de Colombia")
class FechaRespuestaTest {

    private static final ZoneOffset OFFSET_COLOMBIA = ZoneOffset.ofHours(-5);

    @Test
    @DisplayName("US-22 — un valor generado en UTC sale cinco horas antes con offset -05:00")
    void testUtcAColombia() {
        OffsetDateTime resultado = FechaRespuesta.aColombia(LocalDateTime.of(2026, 11, 5, 15, 0), ZoneOffset.UTC);

        assertEquals(OffsetDateTime.of(2026, 11, 5, 10, 0, 0, 0, OFFSET_COLOMBIA), resultado);
    }

    @Test
    @DisplayName("US-22 — después de las 7 p. m. de Colombia, el día UTC siguiente vuelve al día de Colombia")
    void testUtcAColombiaCambiaDeDia() {
        OffsetDateTime resultado = FechaRespuesta.aColombia(
                LocalDateTime.of(2026, 11, 6, 1, 28, 7, 616_432_000), ZoneOffset.UTC);

        assertEquals(OffsetDateTime.of(2026, 11, 5, 20, 28, 7, 616_432_000, OFFSET_COLOMBIA), resultado);
        assertEquals("2026-11-05T20:28:07.616432-05:00", resultado.toString());
    }

    @Test
    @DisplayName("US-22 — un valor generado en hora de Colombia conserva la hora y agrega el offset")
    void testColombiaAColombia() {
        OffsetDateTime resultado = FechaRespuesta.aColombia(
                LocalDateTime.of(2026, 11, 5, 20, 28), ZoneId.of("America/Bogota"));

        assertEquals(OffsetDateTime.of(2026, 11, 5, 20, 28, 0, 0, OFFSET_COLOMBIA), resultado);
    }

    @Test
    @DisplayName("US-22 — la sobrecarga sin zona usa la zona del JVM (UTC en los tests, como Render)")
    void testZonaDelSistema() {
        LocalDateTime valor = LocalDateTime.of(2026, 11, 6, 1, 28);

        assertEquals(FechaRespuesta.aColombia(valor, ZoneId.systemDefault()), FechaRespuesta.aColombia(valor));
    }

    @Test
    @DisplayName("US-22 — null se mantiene null")
    void testNulo() {
        assertNull(FechaRespuesta.aColombia(null));
        assertNull(FechaRespuesta.aColombia(null, ZoneOffset.UTC));
    }
}
