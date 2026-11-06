// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.dto;

import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.EstadoPublicacion;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.UnidadMedida;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfertaResponse {

    private UUID id;
    private Producto producto;
    private String productoOtro;
    private String descripcion;
    private BigDecimal cantidadDisponible;
    private UnidadMedida unidadMedida;
    private BigDecimal precioUnitario;
    private EstadoCultivo estadoCultivo;
    private LocalDate fechaEstimadaCosecha;
    private ModalidadLogistica modalidadLogistica;
    private boolean activa;
    // US-09: calculado desde activa; el frontend lo usa para el badge "Agotado"
    private EstadoPublicacion estadoPublicacion;
    @Schema(description = "Fecha y hora de publicación en hora de Colombia", example = "2026-11-05T20:27:55.120934-05:00")
    private OffsetDateTime fechaCreacion;
    private UUID fincaId;
    private String fincaNombre;
    private Municipio municipio;
}
