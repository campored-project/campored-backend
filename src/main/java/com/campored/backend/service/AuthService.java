// ============================================================
// FEATURE: US-01 — Registro de Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Autenticación segura con JWT y validación de datos
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.AuthResponse;
import com.campored.backend.dto.LoginRequest;
import com.campored.backend.dto.RegistroCompradorRequest;
import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.CredencialesInvalidasException;
import com.campored.backend.util.UsuarioMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Credenciales inválidas";

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final String hashFicticio;

    public AuthService(UsuarioService usuarioService, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthResponse registrarProductor(RegistroProductorRequest request) {
        Usuario productor = usuarioService.registrarProductor(request);
        return construirRespuesta(productor);
    }

    // ============================================================
    // FEATURE: US-03 — Registro de Comprador Comercial (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Registro de negocios compradores con JWT
    // ============================================================
    public AuthResponse registrarComprador(RegistroCompradorRequest request) {
        Usuario comprador = usuarioService.registrarComprador(request);
        return construirRespuesta(comprador);
    }

    // ============================================================
    // FEATURE: US-02 — Login de Productor (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Inicio de sesión con correo y contraseña, emite JWT
    // ============================================================
    public AuthResponse login(LoginRequest request) {
        Optional<Usuario> usuario = usuarioService.buscarPorCorreo(request.getCorreo());

        // Se compara contra un hash ficticio cuando el correo no existe para que el tiempo
        // de respuesta no revele qué correos están registrados
        String hash = usuario.map(Usuario::getContrasenaHash).orElse(hashFicticio);
        boolean coincide = passwordEncoder.matches(request.getContrasena(), hash);

        if (usuario.isEmpty() || !coincide) {
            log.warn("Intento de inicio de sesión fallido para {}", request.getCorreo());
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        log.info("Inicio de sesión exitoso: {}", usuario.get().getId());
        return construirRespuesta(usuario.get());
    }

    private AuthResponse construirRespuesta(Usuario usuario) {
        return AuthResponse.builder()
                .token(jwtService.generarToken(usuario))
                .expiraEnMs(jwtService.getExpiracionMs())
                .usuario(UsuarioMapper.toResponse(usuario))
                .build();
    }
}
