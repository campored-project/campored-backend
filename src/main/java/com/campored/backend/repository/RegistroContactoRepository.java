// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Registro de cada contacto del comprador con un productor por WhatsApp o llamada
// ============================================================
package com.campored.backend.repository;

import com.campored.backend.entity.RegistroContacto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistroContactoRepository extends JpaRepository<RegistroContacto, UUID> {

    // US-10: una oferta con contactos no se puede eliminar
    boolean existsByOfertaId(UUID ofertaId);

    // La oferta, su finca y el productor se cargan en la misma consulta; las relaciones uno a uno inversas
    // son EAGER y sin incluirlas Hibernate lanza una consulta por cada contacto. El id desempata contactos
    // con la misma fecha para que el orden sea estable
    @EntityGraph(attributePaths = {
            "oferta", "oferta.finca", "oferta.finca.productor", "oferta.productor", "oferta.productor.negocio"})
    List<RegistroContacto> findByCompradorIdOrderByFechaContactoDescIdAsc(UUID compradorId);
}
