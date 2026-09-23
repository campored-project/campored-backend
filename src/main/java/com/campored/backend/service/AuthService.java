// ============================================================
// FEATURE: US-01 — Registro de Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Autenticación segura con JWT y validación de datos
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.AuthResponse;
import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.entity.Usuario;
import com.campored.backend.util.UsuarioMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;

    public AuthResponse registrarProductor(RegistroProductorRequest request) {
        Usuario productor = usuarioService.registrarProductor(request);
        return AuthResponse.builder()
                .token(jwtService.generarToken(productor))
                .expiraEnMs(jwtService.getExpiracionMs())
                .usuario(UsuarioMapper.toResponse(productor))
                .build();
    }
}
