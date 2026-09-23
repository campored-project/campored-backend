// ============================================================
// FEATURE: US-01 — Registro de Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Autenticación segura con JWT y validación de datos
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.exception.ResourceAlreadyExistsException;
import com.campored.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Usuario registrarProductor(RegistroProductorRequest request) {
        String correo = normalizarCorreo(request.getCorreo());
        if (usuarioRepository.existsByCorreo(correo)) {
            log.warn("Intento de registro con correo duplicado: {}", correo);
            throw new ResourceAlreadyExistsException("Este correo ya está registrado");
        }

        Municipio municipio = Municipio.desde(request.getMunicipio())
                .orElseThrow(() -> new InvalidInputException(
                        "Municipio no soportado: " + request.getMunicipio()));

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre().trim());
        usuario.setCorreo(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(request.getContrasena()));
        usuario.setTelefono(request.getTelefono());
        usuario.setWhatsapp(request.getWhatsapp());
        usuario.setRol(Rol.PRODUCTOR);
        usuario.asignarFinca(crearFinca(request, municipio));

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Productor registrado: {}", guardado.getId());
        return guardado;
    }

    private Finca crearFinca(RegistroProductorRequest request, Municipio municipio) {
        Finca finca = new Finca();
        finca.setNombreFinca(request.getNombreFinca().trim());
        finca.setVereda(request.getVereda().trim());
        finca.setMunicipio(municipio);
        return finca;
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
