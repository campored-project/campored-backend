package com.campored.backend.repository;

import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Municipio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FincaRepository extends JpaRepository<Finca, UUID> {

    List<Finca> findByMunicipio(Municipio municipio);
}
