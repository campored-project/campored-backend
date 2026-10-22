// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.dto.ErrorResponse;
import com.campored.backend.dto.OfertaResponse;
import com.campored.backend.service.OfertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ofertas")
@PreAuthorize("hasRole('PRODUCTOR')")
@RequiredArgsConstructor
@Tag(name = "Ofertas", description = "Publicación y consulta de ofertas del productor")
public class OfertaController {

    private final OfertaService ofertaService;

    @PostMapping
    @Operation(summary = "Publicar una oferta en la finca del productor autenticado",
            description = "Un cultivo EN_CRECIMIENTO requiere fecha estimada de cosecha futura; "
                    + "uno DISPONIBLE no la admite. El producto OTRO requiere productoOtro")
    @ApiResponse(responseCode = "201", description = "Oferta publicada; el header Location apunta al recurso")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es productor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "El productor no tiene una finca registrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<OfertaResponse> crearOferta(@AuthenticationPrincipal UUID productorId,
                                                      @Valid @RequestBody CrearOfertaRequest request) {
        OfertaResponse oferta = ofertaService.crearOferta(productorId, request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(oferta.getId())
                .toUri();
        return ResponseEntity.created(ubicacion).body(oferta);
    }

    // ============================================================
    // FEATURE: US-12 — Listar mis Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta de las ofertas publicadas por el productor autenticado
    // ============================================================
    @GetMapping("/mis-ofertas")
    @Operation(summary = "Listar las ofertas del productor autenticado",
            description = "Ordenadas de la más reciente a la más antigua. Devuelve una lista vacía si no hay ofertas")
    @ApiResponse(responseCode = "200", description = "Ofertas del productor")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es productor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<OfertaResponse> listarMisOfertas(@AuthenticationPrincipal UUID productorId) {
        return ofertaService.listarMisOfertas(productorId);
    }
}
