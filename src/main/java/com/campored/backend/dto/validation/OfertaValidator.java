// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.dto.validation;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.Producto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.ZoneId;

public class OfertaValidator implements ConstraintValidator<OfertaValida, CrearOfertaRequest> {

    // El servidor corre en UTC; "hoy" debe ser el día calendario del productor en Colombia
    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @Override
    public boolean isValid(CrearOfertaRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean fechaValida = validarFechaCosecha(request, context);
        boolean productoValido = validarProductoOtro(request, context);
        return fechaValida && productoValido;
    }

    private boolean validarFechaCosecha(CrearOfertaRequest request, ConstraintValidatorContext context) {
        LocalDate fecha = request.getFechaEstimadaCosecha();
        if (request.getEstadoCultivo() == EstadoCultivo.EN_CRECIMIENTO) {
            if (fecha == null) {
                return rechazar(context, "fechaEstimadaCosecha", CrearOfertaRequest.MENSAJE_FECHA_COSECHA_REQUERIDA);
            }
            if (!fecha.isAfter(LocalDate.now(ZONA_COLOMBIA))) {
                return rechazar(context, "fechaEstimadaCosecha", CrearOfertaRequest.MENSAJE_FECHA_COSECHA_FUTURA);
            }
        }
        return true;
    }

    private boolean validarProductoOtro(CrearOfertaRequest request, ConstraintValidatorContext context) {
        String productoOtro = request.getProductoOtro();
        if (request.getProducto() == Producto.OTRO && (productoOtro == null || productoOtro.isBlank())) {
            return rechazar(context, "productoOtro", CrearOfertaRequest.MENSAJE_PRODUCTO_OTRO_REQUERIDO);
        }
        return true;
    }

    private boolean rechazar(ConstraintValidatorContext context, String campo, String mensaje) {
        context.buildConstraintViolationWithTemplate(mensaje)
                .addPropertyNode(campo)
                .addConstraintViolation();
        return false;
    }
}
