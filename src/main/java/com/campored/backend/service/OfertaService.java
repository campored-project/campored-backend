// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.dto.FiltroCatalogoOfertas;
import com.campored.backend.dto.OfertaCatalogoResponse;
import com.campored.backend.dto.OfertaResponse;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.OfertaSpecifications;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.util.OfertaMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfertaService {

    public static final String MENSAJE_PRODUCTOR_SIN_FINCA =
            "El productor debe tener una finca registrada para publicar ofertas";
    public static final int TAMANO_MAXIMO_PAGINA = 50;

    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fechaCreacion", "precioUnitario", "fechaEstimadaCosecha");

    private static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "fechaCreacion");
    // Desempate estable: sin él, ofertas con el mismo precio o fecha pueden repetirse entre páginas
    private static final Sort DESEMPATE = Sort.by(Sort.Direction.ASC, "id");

    private static final Logger log = LoggerFactory.getLogger(OfertaService.class);

    private final OfertaRepository ofertaRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    @Transactional
    public OfertaResponse crearOferta(UUID productorId, CrearOfertaRequest request) {
        Usuario productor = buscarProductor(productorId);
        Finca finca = productor.getFinca();
        if (finca == null) {
            log.warn("Productor {} intentó publicar una oferta sin finca registrada", productorId);
            throw new ReglaNegocioException(MENSAJE_PRODUCTOR_SIN_FINCA);
        }

        Oferta oferta = new Oferta();
        oferta.setProductor(productor);
        oferta.setFinca(finca);
        oferta.setProducto(request.getProducto());
        // La BD exige producto_otro NULL cuando el producto pertenece al catálogo
        oferta.setProductoOtro(request.getProducto() == Producto.OTRO ? textoOpcional(request.getProductoOtro()) : null);
        oferta.setDescripcion(textoOpcional(request.getDescripcion()));
        oferta.setCantidadDisponible(request.getCantidadDisponible());
        oferta.setUnidadMedida(request.getUnidadMedida());
        oferta.setPrecioUnitario(request.getPrecioUnitario());
        oferta.setEstadoCultivo(request.getEstadoCultivo());
        oferta.setFechaEstimadaCosecha(request.getFechaEstimadaCosecha());
        oferta.setModalidadLogistica(request.getModalidadLogistica());
        oferta.setActiva(true);

        Oferta guardada = ofertaRepository.save(oferta);
        log.info("Oferta {} publicada por el productor {}", guardada.getId(), productorId);
        return OfertaMapper.toResponse(guardada);
    }

    // ============================================================
    // FEATURE: US-12 — Listar mis Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta de las ofertas publicadas por el productor autenticado
    // ============================================================
    @Transactional(readOnly = true)
    public List<OfertaResponse> listarMisOfertas(UUID productorId) {
        return ofertaRepository.findByProductorIdOrderByFechaCreacionDesc(productorId).stream()
                .map(OfertaMapper::toResponse)
                .toList();
    }

    // ============================================================
    // FEATURE: US-07 — Catálogo Público de Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Consulta paginada de ofertas activas con filtros combinables
    // ============================================================
    @Transactional(readOnly = true)
    public Page<OfertaCatalogoResponse> listarCatalogo(FiltroCatalogoOfertas filtro, Pageable pageable) {
        Specification<Oferta> especificacion = Specification.where(OfertaSpecifications.activas())
                .and(OfertaSpecifications.conMunicipio(filtro.municipio()))
                .and(OfertaSpecifications.conEstadoCultivo(filtro.estadoCultivo()))
                .and(OfertaSpecifications.conProducto(filtro.producto()))
                .and(OfertaSpecifications.conModalidadLogistica(filtro.modalidadLogistica()));
        LocalDate hoy = LocalDate.now(clock);
        return ofertaRepository.findAll(especificacion, paginaDelCatalogo(pageable))
                .map(oferta -> OfertaMapper.toCatalogoResponse(oferta, hoy));
    }

    private Pageable paginaDelCatalogo(Pageable pageable) {
        Sort orden = pageable.getSort().isSorted() ? validarOrden(pageable.getSort()) : ORDEN_POR_DEFECTO;
        int tamano = Math.min(pageable.getPageSize(), TAMANO_MAXIMO_PAGINA);
        return PageRequest.of(pageable.getPageNumber(), tamano, orden.and(DESEMPATE));
    }

    private Sort validarOrden(Sort orden) {
        orden.stream()
                .filter(criterio -> !CAMPOS_ORDENABLES.contains(criterio.getProperty()))
                .findFirst()
                .ifPresent(criterio -> {
                    throw new InvalidInputException("No se puede ordenar por '" + criterio.getProperty()
                            + "'. Campos permitidos: fechaCreacion, precioUnitario, fechaEstimadaCosecha");
                });
        return orden;
    }

    private Usuario buscarProductor(UUID productorId) {
        return usuarioRepository.findById(productorId)
                .filter(usuario -> usuario.getRol() == Rol.PRODUCTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
