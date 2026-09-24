// ============================================================
// FEATURE: US-04 — Gestión de Perfil del Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Actualización de teléfono, WhatsApp y canales de contacto
// ============================================================
package com.campored.backend.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = CanalesContactoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CanalesContactoValidos {

    String message() default "Cada canal de contacto habilitado requiere su número";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
