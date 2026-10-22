package com.campored.backend.repository;

import com.campored.backend.entity.Oferta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfertaRepository extends JpaRepository<Oferta, UUID>, JpaSpecificationExecutor<Oferta> {

    // La finca se carga en la misma consulta: el mapeo a OfertaResponse lee su nombre y municipio
    @EntityGraph(attributePaths = "finca")
    List<Oferta> findByProductorIdOrderByFechaCreacionDesc(UUID productorId);

    // ============================================================
    // FEATURE: US-11 / US-13 — Catálogo Público de Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta paginada de ofertas activas con filtros combinables
    // ============================================================
    // Las relaciones uno a uno inversas (Finca.productor, Usuario.negocio) son EAGER por defecto;
    // sin incluirlas aquí Hibernate lanza una consulta adicional por cada oferta de la página
    @Override
    @EntityGraph(attributePaths = {
            "finca", "finca.productor", "finca.productor.negocio", "productor", "productor.negocio"})
    @NonNull
    Page<Oferta> findAll(@Nullable Specification<Oferta> spec, @NonNull Pageable pageable);

    // ============================================================
    // FEATURE: US-12 — Detalle de Oferta (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta pública de una oferta activa con su finca y productor
    // ============================================================
    @EntityGraph(attributePaths = {
            "finca", "finca.productor", "finca.productor.negocio", "productor", "productor.negocio"})
    Optional<Oferta> findByIdAndActivaTrue(UUID id);
}
