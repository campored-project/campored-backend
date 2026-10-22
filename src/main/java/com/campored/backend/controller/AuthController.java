// ============================================================
// FEATURE: US-01 — Registro de Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Autenticación segura con JWT y validación de datos
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.AuthResponse;
import com.campored.backend.dto.ErrorResponse;
import com.campored.backend.dto.LoginRequest;
import com.campored.backend.dto.RegistroCompradorRequest;
import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Registro e inicio de sesión")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro/productor")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un productor con su finca")
    @ApiResponse(responseCode = "201", description = "Productor registrado; incluye el JWT de acceso")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AuthResponse registrarProductor(@Valid @RequestBody RegistroProductorRequest request) {
        return authService.registrarProductor(request);
    }

    // ============================================================
    // FEATURE: US-03 — Registro de Comprador Comercial (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Registro de negocios compradores con JWT
    // ============================================================
    @PostMapping("/registro/comprador")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un comprador comercial con su negocio",
            description = "Para restaurantes, tiendas, minimercados y mayoristas que compran directo a productores")
    @ApiResponse(responseCode = "201", description = "Comprador registrado; incluye el JWT de acceso")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, tipo de negocio o municipio no soportado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AuthResponse registrarComprador(@Valid @RequestBody RegistroCompradorRequest request) {
        return authService.registrarComprador(request);
    }

    // ============================================================
    // FEATURE: US-02 — Login de Productor (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Inicio de sesión con correo y contraseña, emite JWT
    // ============================================================
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con correo y contraseña")
    @ApiResponse(responseCode = "200", description = "Credenciales válidas; incluye el JWT de acceso")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
