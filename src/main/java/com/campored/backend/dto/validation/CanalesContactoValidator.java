// ============================================================
// FEATURE: US-04 — Gestión de Perfil del Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Actualización de teléfono, WhatsApp y canales de contacto
// ============================================================
package com.campored.backend.dto.validation;

import com.campored.backend.dto.ActualizarPerfilProductorRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CanalesContactoValidator
        implements ConstraintValidator<CanalesContactoValidos, ActualizarPerfilProductorRequest> {

    @Override
    public boolean isValid(ActualizarPerfilProductorRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean llamadaValida = validarCanal(request.getCanalLlamadaHabilitado(), request.getTelefono(),
                "telefono", ActualizarPerfilProductorRequest.MENSAJE_TELEFONO_REQUERIDO, context);
        boolean whatsappValido = validarCanal(request.getCanalWhatsappHabilitado(), request.getWhatsapp(),
                "whatsapp", ActualizarPerfilProductorRequest.MENSAJE_WHATSAPP_REQUERIDO, context);
        return llamadaValida && whatsappValido;
    }

    // Un número ausente no se revisa aquí: en un PATCH conserva el valor guardado y lo valida UsuarioService
    private boolean validarCanal(Boolean habilitado, String numero, String campo, String mensaje,
                                 ConstraintValidatorContext context) {
        if (Boolean.TRUE.equals(habilitado) && numero != null && numero.isBlank()) {
            context.buildConstraintViolationWithTemplate(mensaje)
                    .addPropertyNode(campo)
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
