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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private LocalDateTime fechaCreacion;
    private UUID fincaId;
    private String fincaNombre;
    private Municipio municipio;
}
