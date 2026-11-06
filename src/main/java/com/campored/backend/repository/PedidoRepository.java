// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
// ============================================================
package com.campored.backend.repository;

import com.campored.backend.entity.Pedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    // US-10: una oferta con pedidos no se puede eliminar
    boolean existsByOfertaId(UUID ofertaId);

    // El detalle lee la oferta, su finca, el productor y el negocio del comprador; las relaciones uno a uno
    // inversas son EAGER y sin incluirlas Hibernate lanza una consulta por cada una
    @EntityGraph(attributePaths = {
            "oferta", "oferta.finca", "oferta.finca.productor", "oferta.productor", "oferta.productor.negocio",
            "comprador", "comprador.negocio"})
    Optional<Pedido> findDetalleById(UUID id);

    // ============================================================
    // FEATURE: US-23 — Pedidos Recibidos del Productor (Sprint 3)
    // Autor: Cristian Diez
    // Fecha: 2026-11-05
    // Descripción: Pedidos sobre las ofertas del productor autenticado para su panel
    // ============================================================
    // El filtro por productor va en la consulta (join por oferta.productor) para que un productor nunca reciba
    // pedidos de otro. Mismo grafo que findDetalleById: el PedidoResponse completo sale en una sola consulta.
    // El id desempata pedidos creados en el mismo instante para que el orden sea estable
    @EntityGraph(attributePaths = {
            "oferta", "oferta.finca", "oferta.finca.productor", "oferta.productor", "oferta.productor.negocio",
            "comprador", "comprador.negocio"})
    List<Pedido> findByOfertaProductorIdOrderByFechaCreacionDescIdAsc(UUID productorId);
}
