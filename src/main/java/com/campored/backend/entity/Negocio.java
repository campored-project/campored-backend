// ============================================================
// FEATURE: US-03 — Registro de Comprador Comercial (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Registro de negocios compradores con JWT
// ============================================================
package com.campored.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "negocios")
@Getter
@Setter
@NoArgsConstructor
public class Negocio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nombre_negocio", nullable = false, length = 120)
    private String nombreNegocio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_negocio", nullable = false, length = 20)
    private TipoNegocio tipoNegocio;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Municipio municipio;

    @Column(name = "horario_recepcion", length = 120)
    private String horarioRecepcion;

    @Column(name = "notas_acceso", length = 500)
    private String notasAcceso;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;
}
