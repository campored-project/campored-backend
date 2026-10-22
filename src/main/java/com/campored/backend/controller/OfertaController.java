// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.dto.ErrorResponse;
import com.campored.backend.dto.FiltroCatalogoOfertas;
import com.campored.backend.dto.OfertaCatalogoResponse;
import com.campored.backend.dto.OfertaDetalleResponse;
import com.campored.backend.dto.OfertaResponse;
import com.campored.backend.dto.PageResponse;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.service.OfertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final String ROL_COMPRADOR = "ROLE_" + Rol.COMPRADOR.name();

    private final OfertaService ofertaService;

    @PostMapping
    @Operation(summary = "Publicar una oferta en la finca del productor autenticado",
            description = "Un cultivo EN_CRECIMIENTO requiere fecha estimada de cosecha futura; "
                    + "en uno DISPONIBLE se ignora y se guarda como null. El producto OTRO requiere productoOtro")
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
    // FEATURE: US-08 — Editar Oferta (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Reemplazo completo de los campos editables de una oferta activa propia
    // ============================================================
    // Hereda hasRole('PRODUCTOR') de la clase, igual que POST; SecurityConfig solo abre GET en /api/ofertas/{uuid}
    @PutMapping("/{id}")
    @Operation(summary = "Editar una oferta activa del productor autenticado",
            description = "Reemplaza todos los campos editables con las mismas reglas de la creación. "
                    + "No cambia el productor, la finca, la fecha de creación ni el estado de publicación. "
                    + "Las ofertas agotadas no se pueden editar")
    @ApiResponse(responseCode = "200", description = "Oferta actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o id que no es un UUID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado", content = @Content)
    @ApiResponse(responseCode = "403", description = "El usuario no es productor o la oferta es de otro productor",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La oferta no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "La oferta está agotada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OfertaResponse actualizarOferta(@PathVariable UUID id,
                                           @AuthenticationPrincipal UUID productorId,
                                           @Valid @RequestBody CrearOfertaRequest request) {
        return ofertaService.actualizarOferta(id, productorId, request);
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

    // ============================================================
    // FEATURE: US-11 / US-13 — Catálogo Público de Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta paginada de ofertas activas con filtros combinables
    // ============================================================
    // permitAll a nivel de método reemplaza el hasRole('PRODUCTOR') de la clase; SecurityConfig solo deja pasar
    // a usuarios anónimos en GET /api/ofertas y GET /api/ofertas/{uuid}
    @GetMapping
    @PreAuthorize("permitAll()")
    @SecurityRequirements
    @Operation(summary = "Consultar el catálogo público de ofertas activas",
            description = "Endpoint público: no requiere token y responde igual a compradores, productores y visitantes. "
                    + "Solo incluye ofertas activas. Los filtros son opcionales y se combinan entre sí. "
                    + "Por defecto ordena de la más reciente a la más antigua (sort=fechaCreacion,desc); "
                    + "solo se puede ordenar por fechaCreacion, precioUnitario o fechaEstimadaCosecha. "
                    + "El tamaño de página máximo es " + OfertaService.TAMANO_MAXIMO_PAGINA)
    @ApiResponse(responseCode = "200", description = "Página de ofertas que cumplen los filtros")
    @ApiResponse(responseCode = "400", description = "Filtro con un valor fuera del catálogo o campo de orden no permitido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PageResponse<OfertaCatalogoResponse> listarCatalogo(
            @Parameter(description = "Código o nombre del municipio de la finca, sin distinguir mayúsculas ni tildes "
                    + "(SONSON, sonson, Sonsón, SAN_CARLOS, San Carlos)", example = "SONSON")
            @RequestParam(required = false) String municipio,
            @Parameter(description = "EN_CRECIMIENTO o DISPONIBLE")
            @RequestParam(required = false) EstadoCultivo estadoCultivo,
            @Parameter(description = "Producto del catálogo; incluye las ofertas OTRO cuyo nombre coincide con el producto "
                    + "(p. ej. MORA_CASTILLA trae también OTRO \"Mora de castilla\"). OTRO devuelve solo las ofertas OTRO")
            @RequestParam(required = false) Producto producto,
            @Parameter(description = "RECOGIDA_FINCA y DESPACHO_PRODUCTOR incluyen también las ofertas AMBAS; "
                    + "AMBAS devuelve solo las ofertas que admiten las dos modalidades")
            @RequestParam(required = false) ModalidadLogistica modalidadLogistica,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        FiltroCatalogoOfertas filtro = new FiltroCatalogoOfertas(
                resolverMunicipio(municipio), estadoCultivo, producto, modalidadLogistica);
        return PageResponse.of(ofertaService.listarCatalogo(filtro, pageable));
    }

    // ============================================================
    // FEATURE: US-12 — Detalle de Oferta (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta pública de una oferta activa con su finca y productor
    // ============================================================
    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    @SecurityRequirements
    @Operation(summary = "Consultar el detalle público de una oferta activa",
            description = "Endpoint público: no requiere token. Incluye la finca, el nombre del productor y qué canales "
                    + "de contacto tiene activos (whatsappDisponible, llamadaDisponible). telefonoContacto y "
                    + "whatsappContacto solo se envían a compradores autenticados (ROLE_COMPRADOR) y para canales "
                    + "activos; para visitantes anónimos y productores son null. Nunca se expone el correo")
    @ApiResponse(responseCode = "200", description = "Detalle de la oferta")
    @ApiResponse(responseCode = "400", description = "El id no es un UUID válido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La oferta no existe o no está disponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public OfertaDetalleResponse obtenerDetalle(@PathVariable UUID id,
                                                @Parameter(hidden = true) Authentication autenticacion) {
        return ofertaService.obtenerDetalle(id, esComprador(autenticacion));
    }

    // Spring entrega null para visitantes anónimos; un token inválido o expirado también llega como anónimo
    private boolean esComprador(Authentication autenticacion) {
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .anyMatch(permiso -> ROL_COMPRADOR.equals(permiso.getAuthority()));
    }

    private Municipio resolverMunicipio(String municipio) {
        if (municipio == null || municipio.isBlank()) {
            return null;
        }
        return Municipio.desde(municipio)
                .orElseThrow(() -> new InvalidInputException("El municipio '" + municipio + "' no es válido"));
    }
}
