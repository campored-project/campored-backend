// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Formalización del pedido con datos de entrega y estado SOLICITADO
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.CrearPedidoRequest;
import com.campored.backend.dto.PedidoResponse;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.EstadoPedido;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Pedido;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.AccesoDenegadoException;
import com.campored.backend.exception.GlobalExceptionHandler;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.PedidoRepository;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.util.PedidoMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoService {

    public static final String MENSAJE_PERFIL_NO_ENCONTRADO = "Perfil de usuario no encontrado";
    public static final String MENSAJE_OFERTA_NO_ENCONTRADA = OfertaService.MENSAJE_OFERTA_NO_ENCONTRADA;
    public static final String MENSAJE_OFERTA_AGOTADA = "Esta oferta ya no está disponible para pedidos.";
    public static final String MENSAJE_MUNICIPIO_INVALIDO = "El municipio de entrega no es válido.";
    public static final String MENSAJE_CANTIDAD_SUPERA_DISPONIBLE = "La cantidad solicitada supera la disponible.";
    public static final String MENSAJE_FECHA_ANTERIOR_A_HOY = "La fecha preferida no puede ser anterior a hoy.";
    public static final String MENSAJE_FECHA_ANTERIOR_A_COSECHA =
            "La fecha preferida no puede ser anterior a la fecha estimada de cosecha.";
    public static final String MENSAJE_MODALIDAD_REQUERIDA = "Debes seleccionar la modalidad de entrega.";
    public static final String MENSAJE_MODALIDAD_NO_HABILITADA = "La modalidad de entrega no está habilitada en esta oferta.";
    public static final String MENSAJE_PEDIDO_NO_ENCONTRADO = "El pedido no existe";
    public static final String MENSAJE_PEDIDO_AJENO = "No tienes permiso para ver este pedido.";

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final OfertaRepository ofertaRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    // findById y no findByIdAndActivaTrue: una oferta agotada responde 422, distinto de una inexistente (404).
    // El pedido no descuenta cantidadDisponible: el inventario lo sigue manejando el productor
    @Transactional
    public PedidoResponse crearPedido(UUID compradorId, CrearPedidoRequest request) {
        Usuario comprador = buscarComprador(compradorId);
        Oferta oferta = ofertaRepository.findById(request.getOfertaId())
                .orElseThrow(() -> new ResourceNotFoundException(MENSAJE_OFERTA_NO_ENCONTRADA));
        if (!oferta.isActiva()) {
            log.warn("Comprador {} intentó pedir la oferta agotada {}", compradorId, oferta.getId());
            throw new ReglaNegocioException(MENSAJE_OFERTA_AGOTADA);
        }

        // Todos los errores de negocio se devuelven juntos para que el checkout los muestre de una vez
        Map<String, String> errores = new LinkedHashMap<>();
        Municipio municipio = Municipio.desde(request.getMunicipioEntrega()).orElse(null);
        if (municipio == null) {
            errores.put("municipioEntrega", MENSAJE_MUNICIPIO_INVALIDO);
        }
        if (request.getCantidadSolicitada().compareTo(oferta.getCantidadDisponible()) > 0) {
            errores.put("cantidadSolicitada", MENSAJE_CANTIDAD_SUPERA_DISPONIBLE);
        }
        validarFechaPreferida(request.getFechaPreferida(), oferta, errores);
        ModalidadLogistica modalidad = resolverModalidad(request.getModalidadEntrega(), oferta.getModalidadLogistica(),
                errores);
        if (!errores.isEmpty()) {
            log.warn("Pedido rechazado para el comprador {} sobre la oferta {}: {}", compradorId, oferta.getId(),
                    errores.keySet());
            throw new InvalidInputException(GlobalExceptionHandler.MENSAJE_CAMPOS_INVALIDOS, errores);
        }

        Pedido pedido = new Pedido();
        pedido.setOferta(oferta);
        pedido.setComprador(comprador);
        pedido.setCantidadSolicitada(request.getCantidadSolicitada());
        pedido.setPrecioUnitario(oferta.getPrecioUnitario());
        pedido.setPrecioTotalEstimado(calcularTotal(request.getCantidadSolicitada(), oferta.getPrecioUnitario()));
        pedido.setDireccionEntrega(request.getDireccionEntrega().trim());
        pedido.setMunicipioEntrega(municipio);
        pedido.setFechaPreferida(request.getFechaPreferida());
        pedido.setJornada(request.getJornada());
        pedido.setModalidadEntrega(modalidad);
        pedido.setNotasAcceso(textoOpcional(request.getNotasAcceso()));
        pedido.setEstado(EstadoPedido.SOLICITADO);

        Pedido guardado = pedidoRepository.save(pedido);
        log.info("Pedido {} creado por el comprador {} sobre la oferta {}", guardado.getId(), compradorId, oferta.getId());
        return PedidoMapper.toResponse(guardado);
    }

    // Orden de chequeo: existencia (404) y luego que quien consulta sea una de las dos partes (403)
    @Transactional(readOnly = true)
    public PedidoResponse obtenerPedido(UUID pedidoId, UUID usuarioId) {
        Pedido pedido = pedidoRepository.findDetalleById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException(MENSAJE_PEDIDO_NO_ENCONTRADO));
        boolean esComprador = pedido.getComprador().getId().equals(usuarioId);
        boolean esProductor = pedido.getOferta().getProductor().getId().equals(usuarioId);
        if (!esComprador && !esProductor) {
            log.warn("Usuario {} intentó ver el pedido {} sin ser parte de él", usuarioId, pedidoId);
            throw new AccesoDenegadoException(MENSAJE_PEDIDO_AJENO);
        }
        return PedidoMapper.toResponse(pedido);
    }

    private void validarFechaPreferida(LocalDate fechaPreferida, Oferta oferta, Map<String, String> errores) {
        LocalDate fechaCosecha = oferta.getFechaEstimadaCosecha();
        if (fechaPreferida.isBefore(LocalDate.now(clock))) {
            errores.put("fechaPreferida", MENSAJE_FECHA_ANTERIOR_A_HOY);
        } else if (oferta.getEstadoCultivo() == EstadoCultivo.EN_CRECIMIENTO && fechaCosecha != null
                && fechaPreferida.isBefore(fechaCosecha)) {
            errores.put("fechaPreferida", MENSAJE_FECHA_ANTERIOR_A_COSECHA);
        }
    }

    // AMBAS es una opción de la oferta, nunca la modalidad de un pedido: con AMBAS el comprador debe elegir,
    // y si la oferta tiene una sola modalidad se usa esa
    private ModalidadLogistica resolverModalidad(ModalidadLogistica solicitada, ModalidadLogistica ofrecida,
                                                 Map<String, String> errores) {
        if (ofrecida == ModalidadLogistica.AMBAS && solicitada == null) {
            errores.put("modalidadEntrega", MENSAJE_MODALIDAD_REQUERIDA);
            return null;
        }
        if (solicitada == ModalidadLogistica.AMBAS
                || (ofrecida != ModalidadLogistica.AMBAS && solicitada != null && solicitada != ofrecida)) {
            errores.put("modalidadEntrega", MENSAJE_MODALIDAD_NO_HABILITADA);
            return null;
        }
        return solicitada != null ? solicitada : ofrecida;
    }

    private BigDecimal calcularTotal(BigDecimal cantidad, BigDecimal precioUnitario) {
        return cantidad.multiply(precioUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    private Usuario buscarComprador(UUID compradorId) {
        return usuarioRepository.findById(compradorId)
                .filter(usuario -> usuario.getRol() == Rol.COMPRADOR)
                .orElseThrow(() -> new ResourceNotFoundException(MENSAJE_PERFIL_NO_ENCONTRADO));
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
