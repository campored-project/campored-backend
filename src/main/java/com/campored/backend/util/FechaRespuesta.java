// ============================================================
// FEATURE: US-22 — Manejo de Errores y Formato de Respuestas (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-06
// Descripción: Fechas con hora del API en hora de Colombia y con offset explícito
// ============================================================
package com.campored.backend.util;

import com.campored.backend.dto.validation.OfertaValidator;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// Las entidades guardan LocalDateTime sin zona; aquí se convierte solo en la frontera del API, para que el
// cliente reciba el instante correcto (p. ej. 2026-11-05T20:28:07-05:00) sin adivinar la zona
public final class FechaRespuesta {

    private FechaRespuesta() {
    }

    // systemDefault() porque es la zona con la que @CreationTimestamp genera el valor y con la que Hibernate lo
    // lee de vuelta (hibernate.jdbc.time_zone=UTC convierte entre esa zona y la columna): UTC en Render y la hora
    // local del equipo en desarrollo
    public static OffsetDateTime aColombia(LocalDateTime valor) {
        return aColombia(valor, ZoneId.systemDefault());
    }

    public static OffsetDateTime aColombia(LocalDateTime valor, ZoneId zonaOrigen) {
        if (valor == null) {
            return null;
        }
        return valor.atZone(zonaOrigen)
                .withZoneSameInstant(OfertaValidator.ZONA_COLOMBIA)
                .toOffsetDateTime();
    }
}
