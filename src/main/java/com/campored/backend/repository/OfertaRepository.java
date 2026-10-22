package com.campored.backend.repository;

import com.campored.backend.entity.Oferta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfertaRepository extends JpaRepository<Oferta, UUID> {

    // La finca se carga en la misma consulta: el mapeo a OfertaResponse lee su nombre y municipio
    @EntityGraph(attributePaths = "finca")
    List<Oferta> findByProductorIdOrderByFechaCreacionDesc(UUID productorId);
}
