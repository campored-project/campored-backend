// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Registro de cada contacto del comprador con un productor por WhatsApp o llamada
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.ContactoResponse;
import com.campored.backend.dto.ErrorResponse;
import com.campored.backend.dto.RegistrarContactoRequest;
import com.campored.backend.service.ContactoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contactos")
@PreAuthorize("hasRole('COMPRADOR')")
@RequiredArgsConstructor
@Tag(name = "Contactos", description = "Historial de contactos del comprador con productores")
public class ContactoController {

    private final ContactoService contactoService;

    // Sin header Location: no existe GET /api/contactos/{id} al que apuntar; el registro se consulta en /mios
    @PostMapping
    @Operation(summary = "Registrar un contacto con el productor de una oferta",
            description = "El frontend lo envía al abrir wa.me o tel:. Se registra aunque la oferta esté agotada. "
                    + "El canal debe estar habilitado por el productor y tener número. pedidoId es opcional y, "
                    + "si llega, debe ser un pedido del comprador sobre la misma oferta")
    @ApiResponse(responseCode = "201", description = "Contacto registrado")
    @ApiResponse(responseCode = "400", description = "Falta ofertaId o canal, el canal está fuera del catálogo "
            + "o un id no es un UUID", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es comprador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La oferta no existe o el perfil del usuario autenticado no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "El canal no está habilitado o el pedido no corresponde a la oferta",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ContactoResponse> registrarContacto(@AuthenticationPrincipal UUID compradorId,
                                                              @Valid @RequestBody RegistrarContactoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactoService.registrarContacto(compradorId, request));
    }

    @GetMapping("/mios")
    @Operation(summary = "Listar el historial de contactos del comprador autenticado",
            description = "Del más reciente al más antiguo, sin paginar. Lista vacía si no hay contactos. "
                    + "No incluye números de contacto")
    @ApiResponse(responseCode = "200", description = "Contactos del comprador")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es comprador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<ContactoResponse> listarMisContactos(@AuthenticationPrincipal UUID compradorId) {
        return contactoService.listarMisContactos(compradorId);
    }
}
