// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Registro de cada contacto del comprador con un productor por WhatsApp o llamada
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.ContactoResponse;
import com.campored.backend.dto.ProductorPublicoResponse;
import com.campored.backend.dto.RegistrarContactoRequest;
import com.campored.backend.entity.CanalContacto;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Pedido;
import com.campored.backend.entity.RegistroContacto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.PedidoRepository;
import com.campored.backend.repository.RegistroContactoRepository;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.util.ContactoMapper;
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
public class ContactoService {

    public static final String MENSAJE_PERFIL_NO_ENCONTRADO = PedidoService.MENSAJE_PERFIL_NO_ENCONTRADO;
    public static final String MENSAJE_OFERTA_NO_ENCONTRADA = OfertaService.MENSAJE_OFERTA_NO_ENCONTRADA;
    public static final String MENSAJE_CANAL_NO_HABILITADO = "El productor no tiene habilitado este canal de contacto.";
    public static final String MENSAJE_PEDIDO_NO_CORRESPONDE = "El pedido no corresponde a esta oferta.";

    private static final Logger log = LoggerFactory.getLogger(ContactoService.class);

    private final RegistroContactoRepository registroContactoRepository;
    private final OfertaRepository ofertaRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;

    // findById y no findByIdAndActivaTrue: el historial registra el contacto aunque la oferta ya esté agotada
    @Transactional
    public ContactoResponse registrarContacto(UUID compradorId, RegistrarContactoRequest request) {
        Usuario comprador = buscarComprador(compradorId);
        Oferta oferta = ofertaRepository.findById(request.getOfertaId())
                .orElseThrow(() -> new ResourceNotFoundException(MENSAJE_OFERTA_NO_ENCONTRADA));
        if (!canalDisponible(oferta.getProductor(), request.getCanal())) {
            log.warn("Comprador {} intentó registrar un contacto por {} no habilitado en la oferta {}",
                    compradorId, request.getCanal(), oferta.getId());
            throw new ReglaNegocioException(MENSAJE_CANAL_NO_HABILITADO);
        }
        Pedido pedido = resolverPedido(request.getPedidoId(), compradorId, oferta.getId());

        RegistroContacto registro = new RegistroContacto();
        registro.setComprador(comprador);
        registro.setOferta(oferta);
        registro.setPedido(pedido);
        registro.setCanal(request.getCanal());

        // saveAndFlush: @CreationTimestamp se asigna en el INSERT y la respuesta debe llevar fechaContacto
        RegistroContacto guardado = registroContactoRepository.saveAndFlush(registro);
        log.info("Contacto {} registrado por el comprador {} con la oferta {} por {}",
                guardado.getId(), compradorId, oferta.getId(), request.getCanal());
        return ContactoMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<ContactoResponse> listarMisContactos(UUID compradorId) {
        return registroContactoRepository.findByCompradorIdOrderByFechaContactoDescIdAsc(compradorId).stream()
                .map(ContactoMapper::toResponse)
                .toList();
    }

    // Misma regla que el detalle de la oferta y el pedido: canal habilitado y con número registrado
    private boolean canalDisponible(Usuario productor, CanalContacto canal) {
        ProductorPublicoResponse contacto = OfertaMapper.toProductorPublico(productor, false);
        return canal == CanalContacto.WHATSAPP ? contacto.whatsappDisponible() : contacto.llamadaDisponible();
    }

    // Un pedido inexistente, de otro comprador o de otra oferta responde igual para no revelar si existe
    private Pedido resolverPedido(UUID pedidoId, UUID compradorId, UUID ofertaId) {
        if (pedidoId == null) {
            return null;
        }
        return pedidoRepository.findById(pedidoId)
                .filter(pedido -> pedido.getComprador().getId().equals(compradorId)
                        && pedido.getOferta().getId().equals(ofertaId))
                .orElseThrow(() -> {
                    log.warn("Comprador {} intentó asociar el pedido {} a un contacto con la oferta {}",
                            compradorId, pedidoId, ofertaId);
                    return new ReglaNegocioException(MENSAJE_PEDIDO_NO_CORRESPONDE);
                });
    }

    private Usuario buscarComprador(UUID compradorId) {
        return usuarioRepository.findById(compradorId)
                .filter(usuario -> usuario.getRol() == Rol.COMPRADOR)
                .orElseThrow(() -> new ResourceNotFoundException(MENSAJE_PERFIL_NO_ENCONTRADO));
    }
}
