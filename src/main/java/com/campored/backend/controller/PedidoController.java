// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.CrearPedidoRequest;
import com.campored.backend.dto.ErrorResponse;
import com.campored.backend.dto.PedidoResponse;
import com.campored.backend.service.PedidoService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Checkout y consulta de pedidos entre comprador y productor")
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    @PreAuthorize("hasRole('COMPRADOR')")
    @Operation(summary = "Crear un pedido sobre una oferta activa",
            description = "El comprador se toma del token. El precio unitario se copia de la oferta y el total lo calcula "
                    + "el backend. El pedido queda SOLICITADO y no descuenta la cantidad disponible de la oferta. "
                    + "Los errores de validación y de negocio por campo llegan juntos en errores.<campo>")
    @ApiResponse(responseCode = "201", description = "Pedido creado; el header Location apunta al recurso")
    @ApiResponse(responseCode = "400", description = "Campos inválidos: obligatorios, municipio fuera del catálogo, "
            + "cantidad mayor a la disponible, fecha anterior a hoy o a la cosecha, o modalidad no habilitada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es comprador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La oferta no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "La oferta está agotada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponse> crearPedido(@AuthenticationPrincipal UUID compradorId,
                                                      @Valid @RequestBody CrearPedidoRequest request) {
        PedidoResponse pedido = pedidoService.crearPedido(compradorId, request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(pedido.id())
                .toUri();
        return ResponseEntity.created(ubicacion).body(pedido);
    }

    // ============================================================
    // FEATURE: US-23 — Pedidos Recibidos del Productor (Sprint 3)
    // Autor: Cristian Diez
    // Fecha: 2026-11-05
    // Descripción: Pedidos sobre las ofertas del productor autenticado para su panel
    // ============================================================
    // Spring prioriza la ruta literal sobre /{id}: "recibidos" nunca llega a obtenerPedido como UUID inválido
    @GetMapping("/recibidos")
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Listar los pedidos recibidos en las ofertas del productor autenticado",
            description = "Del más reciente al más antiguo, sin paginar. Incluye los pedidos de ofertas agotadas y "
                    + "el contacto de cada comprador. Lista vacía si no hay pedidos. El contador de pendientes se "
                    + "calcula en el cliente con el tamaño de la lista")
    @ApiResponse(responseCode = "200", description = "Pedidos recibidos")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es productor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El perfil del productor del token ya no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<PedidoResponse> listarRecibidos(@AuthenticationPrincipal UUID productorId) {
        return pedidoService.listarRecibidos(productorId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPRADOR', 'PRODUCTOR')")
    @Operation(summary = "Consultar un pedido",
            description = "Solo lo ven el comprador que lo creó y el productor dueño de la oferta. Incluye los números "
                    + "del productor para los canales que tiene habilitados y el contacto del comprador")
    @ApiResponse(responseCode = "200", description = "Detalle del pedido")
    @ApiResponse(responseCode = "400", description = "El id no es un UUID válido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario no es parte del pedido o su rol no lo permite",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El pedido no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PedidoResponse obtenerPedido(@PathVariable UUID id, @AuthenticationPrincipal UUID usuarioId) {
        return pedidoService.obtenerPedido(id, usuarioId);
    }
}
