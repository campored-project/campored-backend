package com.campored.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    // Opcional para compradores; la obligatoriedad para productores se valida en RegistroProductorRequest
    @Column(length = 20)
    private String telefono;

    @Column(length = 20)
    private String whatsapp;

    // ============================================================
    // FEATURE: US-04 — Gestión de Perfil del Productor (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Canales por los que el productor acepta ser contactado
    // ============================================================
    @ColumnDefault("false")
    @Column(name = "canal_whatsapp_habilitado", nullable = false)
    private boolean canalWhatsappHabilitado;

    @ColumnDefault("false")
    @Column(name = "canal_llamada_habilitado", nullable = false)
    private boolean canalLlamadaHabilitado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "finca_id", unique = true)
    private Finca finca;

    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true)
    private Negocio negocio;

    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    public void asignarFinca(Finca finca) {
        if (negocio != null) {
            throw new IllegalStateException("Un usuario con negocio no puede tener finca");
        }
        this.finca = finca;
        finca.setProductor(this);
    }

    // ============================================================
    // FEATURE: US-03 — Registro de Comprador Comercial (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Registro de negocios compradores con JWT
    // ============================================================
    public void asignarNegocio(Negocio negocio) {
        if (finca != null) {
            throw new IllegalStateException("Un usuario con finca no puede tener negocio");
        }
        this.negocio = negocio;
        negocio.setUsuario(this);
    }

    @PrePersist
    @PreUpdate
    void validarPerfilUnico() {
        if (finca != null && negocio != null) {
            throw new IllegalStateException("Un usuario no puede tener finca y negocio a la vez");
        }
    }
}
