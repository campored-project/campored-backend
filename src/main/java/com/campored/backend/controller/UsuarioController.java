// ============================================================
// FEATURE: US-04 — Gestión de Perfil del Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Actualización de teléfono, WhatsApp y canales de contacto
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.ActualizarPerfilCompradorRequest;
import com.campored.backend.dto.ActualizarPerfilProductorRequest;
import com.campored.backend.dto.ErrorResponse;
import com.campored.backend.dto.UsuarioResponse;
import com.campored.backend.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Perfil del usuario autenticado")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PatchMapping("/perfil/productor")
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Actualizar los datos de contacto del productor autenticado",
            description = "Solo se modifican los campos enviados. Un canal habilitado requiere su número de contacto")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o canal habilitado sin número",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es productor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UsuarioResponse actualizarPerfilProductor(@AuthenticationPrincipal UUID usuarioId,
                                                     @Valid @RequestBody ActualizarPerfilProductorRequest request) {
        return usuarioService.actualizarPerfilProductor(usuarioId, request);
    }

    // ============================================================
    // FEATURE: US-05 — Gestión de Perfil del Comprador Comercial (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Actualización de dirección, municipio y datos de entrega del negocio
    // ============================================================
    @PatchMapping("/perfil/comprador")
    @PreAuthorize("hasRole('COMPRADOR')")
    @Operation(summary = "Actualizar los datos de entrega y contacto del comprador autenticado",
            description = "Solo se modifican los campos enviados. La dirección no puede quedar vacía")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, dirección vacía o municipio no soportado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es comprador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UsuarioResponse actualizarPerfilComprador(@AuthenticationPrincipal UUID usuarioId,
                                                     @Valid @RequestBody ActualizarPerfilCompradorRequest request) {
        return usuarioService.actualizarPerfilComprador(usuarioId, request);
    }
}
