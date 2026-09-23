package com.campored.backend.repository;

import com.campored.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    boolean existsByCorreo(String correo);

    Optional<Usuario> findByCorreo(String correo);
}
