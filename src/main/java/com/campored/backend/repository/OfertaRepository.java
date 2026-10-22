package com.campored.backend.repository;

import com.campored.backend.entity.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OfertaRepository extends JpaRepository<Oferta, UUID> {
}
