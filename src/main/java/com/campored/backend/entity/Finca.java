package com.campored.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "fincas")
@Getter
@Setter
@NoArgsConstructor
public class Finca {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nombre_finca", nullable = false, length = 120)
    private String nombreFinca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Municipio municipio;

    @Column(nullable = false, length = 120)
    private String vereda;

    @OneToOne(mappedBy = "finca")
    private Usuario productor;
}
