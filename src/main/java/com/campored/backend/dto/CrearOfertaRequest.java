// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.dto.validation.OfertaValida;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.UnidadMedida;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OfertaValida
@Schema(description = "El productor y la finca se toman del token; no se envían en el cuerpo")
public class CrearOfertaRequest {

    public static final String MENSAJE_FECHA_COSECHA_REQUERIDA =
            "La fecha estimada de cosecha es obligatoria para productos en cultivo.";
    public static final String MENSAJE_FECHA_COSECHA_FUTURA =
            "La fecha estimada de cosecha debe ser posterior a hoy";
    public static final String MENSAJE_PRODUCTO_OTRO_REQUERIDO = "Debes especificar el nombre del producto.";
    public static final String MENSAJE_CANTIDAD_REQUERIDA = "El volumen disponible es obligatorio.";

    @Schema(example = "TOMATE_CHONTO")
    @NotNull(message = "El producto es obligatorio")
    private Producto producto;

    @Schema(example = "Lulo", description = "Solo aplica cuando producto es OTRO")
    @Size(max = 100, message = "El nombre del producto no puede superar 100 caracteres")
    private String productoOtro;

    @Schema(example = "Tomate chonto de primera, cosechado a mano")
    @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
    private String descripcion;

    @Schema(example = "250.00")
    @NotNull(message = MENSAJE_CANTIDAD_REQUERIDA)
    @DecimalMin(value = "0.01", message = "La cantidad disponible debe ser mayor que cero")
    @Digits(integer = 10, fraction = 2, message = "La cantidad admite hasta 10 enteros y 2 decimales")
    private BigDecimal cantidadDisponible;

    @Schema(example = "KILOGRAMO")
    @NotNull(message = "La unidad de medida es obligatoria")
    private UnidadMedida unidadMedida;

    @Schema(example = "3500", description = "Precio en COP por unidad de medida")
    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "1", message = "El precio unitario debe ser de al menos 1 peso")
    @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 10 enteros y 2 decimales")
    private BigDecimal precioUnitario;

    @Schema(example = "EN_CRECIMIENTO")
    @NotNull(message = "El estado del cultivo es obligatorio")
    private EstadoCultivo estadoCultivo;

    @Schema(example = "2026-12-15", description = "Obligatoria si el cultivo está EN_CRECIMIENTO; si está DISPONIBLE se ignora y se guarda como null")
    private LocalDate fechaEstimadaCosecha;

    @Schema(example = "AMBAS")
    @NotNull(message = "La modalidad logística es obligatoria")
    private ModalidadLogistica modalidadLogistica;
}
