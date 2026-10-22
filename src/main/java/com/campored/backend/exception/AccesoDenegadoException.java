// ============================================================
// FEATURE: US-08 — Editar Oferta (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: El usuario tiene el rol correcto pero el recurso pertenece a otro usuario
// ============================================================
package com.campored.backend.exception;

public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String message) {
        super(message);
    }
}
