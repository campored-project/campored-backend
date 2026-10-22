// ============================================================
// FEATURE: US-11 / US-13 — Catálogo Público de Ofertas (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Consulta paginada de ofertas activas con filtros combinables
// ============================================================
package com.campored.backend.repository;

import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Producto;
import com.campored.backend.util.ProductoNormalizer;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

// Cada filtro devuelve null cuando no se envía; Specification.and(null) lo ignora al combinarlos
public final class OfertaSpecifications {

    private OfertaSpecifications() {
    }

    public static Specification<Oferta> activas() {
        return (root, query, cb) -> cb.isTrue(root.get("activa"));
    }

    public static Specification<Oferta> conMunicipio(Municipio municipio) {
        if (municipio == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("finca").get("municipio"), municipio);
    }

    public static Specification<Oferta> conEstadoCultivo(EstadoCultivo estadoCultivo) {
        if (estadoCultivo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("estadoCultivo"), estadoCultivo);
    }

    // Un productor que eligió OTRO y escribió "Mora de castilla" está ofreciendo MORA_CASTILLA
    public static Specification<Oferta> conProducto(Producto producto) {
        if (producto == null) {
            return null;
        }
        if (producto == Producto.OTRO) {
            return (root, query, cb) -> cb.equal(root.get("producto"), Producto.OTRO);
        }
        Set<String> nombresEquivalentes = ProductoNormalizer.candidatos(producto);
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("producto"), producto),
                cb.and(
                        cb.equal(root.get("producto"), Producto.OTRO),
                        cb.lower(cb.trim(root.get("productoOtro"))).in(nombresEquivalentes)));
    }

    // Una oferta AMBAS sirve a quien busca recogida y a quien busca despacho;
    // buscar AMBAS exige que el productor ofrezca las dos opciones
    public static Specification<Oferta> conModalidadLogistica(ModalidadLogistica modalidad) {
        if (modalidad == null) {
            return null;
        }
        List<ModalidadLogistica> compatibles = modalidad == ModalidadLogistica.AMBAS
                ? List.of(ModalidadLogistica.AMBAS)
                : List.of(modalidad, ModalidadLogistica.AMBAS);
        return (root, query, cb) -> root.get("modalidadLogistica").in(compatibles);
    }
}
