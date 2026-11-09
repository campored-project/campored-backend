// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.Jornada;
import com.campored.backend.entity.ModalidadLogistica;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "El comprador se toma del token. El precio y el total los calcula el backend")
public class CrearPedidoRequest {

    public static final String MENSAJE_OFERTA_REQUERIDA = "Debes indicar la oferta del pedido.";
    public static final String MENSAJE_CANTIDAD_REQUERIDA = "La cantidad solicitada es obligatoria.";
    public static final String MENSAJE_CANTIDAD_POSITIVA = "La cantidad solicitada debe ser mayor que cero.";
    public static final String MENSAJE_CANTIDAD_FORMATO = "La cantidad admite hasta 10 enteros y 2 decimales.";
    public static final String MENSAJE_DIRECCION_REQUERIDA = "La dirección de entrega es obligatoria para continuar.";
    public static final String MENSAJE_DIRECCION_LARGA = "La dirección de entrega no puede superar 200 caracteres.";
    public static final String MENSAJE_MUNICIPIO_REQUERIDO = "El municipio de entrega es obligatorio.";
    public static final String MENSAJE_FECHA_REQUERIDA = "La fecha preferida de entrega es obligatoria.";
    public static final String MENSAJE_JORNADA_REQUERIDA = "Debes seleccionar la jornada de entrega: Mañana o Tarde.";
    public static final String MENSAJE_NOTAS_LARGAS = "Las notas de acceso no pueden superar 500 caracteres.";

    @Schema(example = "8f2d6c1e-4b7a-4f3e-9c2d-1a2b3c4d5e6f")
    @NotNull(message = MENSAJE_OFERTA_REQUERIDA)
    private UUID ofertaId;

    @Schema(example = "120.00", description = "En la unidad de medida de la oferta; no puede superar la cantidad disponible")
    @NotNull(message = MENSAJE_CANTIDAD_REQUERIDA)
    @DecimalMin(value = "0.01", message = MENSAJE_CANTIDAD_POSITIVA)
    @Digits(integer = 10, fraction = 2, message = MENSAJE_CANTIDAD_FORMATO)
    private BigDecimal cantidadSolicitada;

    @Schema(example = "Calle 49 # 50-21, local 3")
    @NotBlank(message = MENSAJE_DIRECCION_REQUERIDA)
    @Size(max = 200, message = MENSAJE_DIRECCION_LARGA)
    private String direccionEntrega;

    // String y no Municipio: así un valor fuera del catálogo responde en errores.municipioEntrega y no como JSON inválido
    @Schema(example = "RIONEGRO", description = "Código o nombre del municipio, sin distinguir mayúsculas ni tildes")
    @NotBlank(message = MENSAJE_MUNICIPIO_REQUERIDO)
    private String municipioEntrega;

    @Schema(example = "2026-11-20", description = "No puede ser anterior a hoy (hora Colombia) ni, si la oferta está "
            + "EN_CRECIMIENTO, a la fecha estimada de cosecha")
    @NotNull(message = MENSAJE_FECHA_REQUERIDA)
    private LocalDate fechaPreferida;

    @Schema(example = "MANANA")
    @NotNull(message = MENSAJE_JORNADA_REQUERIDA)
    private Jornada jornada;

    // implementation = String.class: sobre el enum, springdoc sumaría allowableValues a sus constantes y AMBAS seguiría
    @Schema(example = "DESPACHO_PRODUCTOR", nullable = true, implementation = String.class,
            allowableValues = {"RECOGIDA_FINCA", "DESPACHO_PRODUCTOR"},
            description = "RECOGIDA_FINCA o DESPACHO_PRODUCTOR. "
            + "Obligatoria si la oferta es AMBAS; si no, se puede omitir y se usa la modalidad de la oferta")
    private ModalidadLogistica modalidadEntrega;

    @Schema(example = "Entrada por la puerta de carga, preguntar por Laura", nullable = true)
    @Size(max = 500, message = MENSAJE_NOTAS_LARGAS)
    private String notasAcceso;
}
