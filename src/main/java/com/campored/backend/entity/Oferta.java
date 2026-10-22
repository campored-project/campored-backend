// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ofertas")
@Getter
@Setter
@NoArgsConstructor
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false)
    private Usuario productor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finca_id", nullable = false)
    private Finca finca;

    @Enumerated(EnumType.STRING)
    @Column(name = "producto", nullable = false, length = 30)
    private Producto producto;

    @Column(name = "producto_otro", length = 100)
    private String productoOtro;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "cantidad_disponible", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadDisponible;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 30)
    private UnidadMedida unidadMedida;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cultivo", nullable = false, length = 30)
    private EstadoCultivo estadoCultivo;

    @Column(name = "fecha_estimada_cosecha")
    private LocalDate fechaEstimadaCosecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_logistica", nullable = false, length = 30)
    private ModalidadLogistica modalidadLogistica;

    @ColumnDefault("true")
    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
