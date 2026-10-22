// ============================================================
// FEATURE: US-06 — Crear Oferta de Producto (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Publicación de ofertas con estado de cultivo y modalidad logística
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.dto.OfertaResponse;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.util.OfertaMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfertaService {

    public static final String MENSAJE_PRODUCTOR_SIN_FINCA =
            "El productor debe tener una finca registrada para publicar ofertas";

    private static final Logger log = LoggerFactory.getLogger(OfertaService.class);

    private final OfertaRepository ofertaRepository;
    private final UsuarioRepository usuarioRepository;

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

    private Usuario buscarProductor(UUID productorId) {
        return usuarioRepository.findById(productorId)
                .filter(usuario -> usuario.getRol() == Rol.PRODUCTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
