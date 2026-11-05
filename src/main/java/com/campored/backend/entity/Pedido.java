// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false)
    private Oferta oferta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprador_id", nullable = false)
    private Usuario comprador;

    @Column(name = "cantidad_solicitada", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadSolicitada;

    // Copia del precio de la oferta al crear el pedido: si el productor edita la oferta, el pedido no cambia
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "precio_total_estimado", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioTotalEstimado;

    @Column(name = "direccion_entrega", nullable = false, length = 200)
    private String direccionEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "municipio_entrega", nullable = false, length = 40)
    private Municipio municipioEntrega;

    @Column(name = "fecha_preferida", nullable = false)
    private LocalDate fechaPreferida;

    @Enumerated(EnumType.STRING)
    @Column(name = "jornada", nullable = false, length = 10)
    private Jornada jornada;

    // Siempre la modalidad concreta elegida; AMBAS solo existe en la oferta y lo impiden el servicio y el CHECK
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_entrega", nullable = false, length = 30)
    private ModalidadLogistica modalidadEntrega;

    @Column(name = "notas_acceso", length = 500)
    private String notasAcceso;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'SOLICITADO'")
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.SOLICITADO;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}
