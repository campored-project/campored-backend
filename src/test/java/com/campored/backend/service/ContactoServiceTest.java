// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Reglas del registro de contactos: comprador, oferta, canal disponible y pedido asociado
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.ContactoResponse;
import com.campored.backend.dto.RegistrarContactoRequest;
import com.campored.backend.entity.CanalContacto;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Pedido;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.RegistroContacto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.UnidadMedida;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.PedidoRepository;
import com.campored.backend.repository.RegistroContactoRepository;
import com.campored.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ContactoService - Registro e historial de contactos")
class ContactoServiceTest {

    private static final LocalDateTime FECHA_CONTACTO = LocalDateTime.of(2026, 11, 5, 15, 0);

    @Mock
    private RegistroContactoRepository registroContactoRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ContactoService contactoService;

    private Usuario comprador;
    private Usuario productor;
    private Oferta oferta;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        productor = new Usuario();
        productor.setId(UUID.randomUUID());
        productor.setNombre("María Gómez");
        productor.setRol(Rol.PRODUCTOR);
        productor.setWhatsapp("+573109876543");
        productor.setTelefono("+573001234567");
        productor.setCanalWhatsappHabilitado(true);
        productor.setCanalLlamadaHabilitado(true);
        Finca finca = new Finca();
        finca.setId(UUID.randomUUID());
        finca.setNombreFinca("Finca El Roble");
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("La Honda");
        productor.asignarFinca(finca);

        comprador = new Usuario();
        comprador.setId(UUID.randomUUID());
        comprador.setNombre("Laura Restrepo");
        comprador.setRol(Rol.COMPRADOR);

        oferta = new Oferta();
        oferta.setId(UUID.randomUUID());
        oferta.setProductor(productor);
        oferta.setFinca(finca);
        oferta.setProducto(Producto.TOMATE_CHONTO);
        oferta.setCantidadDisponible(new BigDecimal("250.00"));
        oferta.setUnidadMedida(UnidadMedida.KILOGRAMO);
        oferta.setPrecioUnitario(new BigDecimal("3500.00"));
        oferta.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        oferta.setModalidadLogistica(ModalidadLogistica.AMBAS);
        oferta.setActiva(true);
    }

    private RegistrarContactoRequest request(CanalContacto canal, UUID pedidoId) {
        return RegistrarContactoRequest.builder().ofertaId(oferta.getId()).canal(canal).pedidoId(pedidoId).build();
    }

    private void simularCompradorYOferta() {
        when(usuarioRepository.findById(comprador.getId())).thenReturn(Optional.of(comprador));
        when(ofertaRepository.findById(oferta.getId())).thenReturn(Optional.of(oferta));
        when(registroContactoRepository.saveAndFlush(any(RegistroContacto.class))).thenAnswer(invocacion -> {
            RegistroContacto registro = invocacion.getArgument(0);
            registro.setId(UUID.randomUUID());
            registro.setFechaContacto(FECHA_CONTACTO);
            return registro;
        });
    }

    private RegistroContacto registroGuardado() {
        ArgumentCaptor<RegistroContacto> captor = ArgumentCaptor.forClass(RegistroContacto.class);
        verify(registroContactoRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Pedido pedidoDe(Usuario dueno, Oferta ofertaPedida) {
        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID());
        pedido.setComprador(dueno);
        pedido.setOferta(ofertaPedida);
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private void assertPedidoRechazado(UUID pedidoId) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> contactoService.registrarContacto(comprador.getId(), request(CanalContacto.WHATSAPP, pedidoId)));

        assertEquals(ContactoService.MENSAJE_PEDIDO_NO_CORRESPONDE, ex.getMessage());
        verify(registroContactoRepository, never()).saveAndFlush(any());
    }

    @Nested
    @DisplayName("Registrar contacto")
    class RegistrarContacto {

        @Test
        @DisplayName("US-19 Esc 1 — registra el comprador, la oferta, el canal y la fecha del contacto")
        void testRegistrarContactoExitoso() {
            simularCompradorYOferta();

            ContactoResponse respuesta = contactoService.registrarContacto(comprador.getId(),
                    request(CanalContacto.WHATSAPP, null));

            RegistroContacto guardado = registroGuardado();
            assertSame(comprador, guardado.getComprador());
            assertSame(oferta, guardado.getOferta());
            assertEquals(CanalContacto.WHATSAPP, guardado.getCanal());
            assertNull(guardado.getPedido());

            assertEquals(guardado.getId(), respuesta.id());
            assertEquals(FECHA_CONTACTO, respuesta.fechaContacto());
            assertEquals(CanalContacto.WHATSAPP, respuesta.canal());
            assertEquals(oferta.getId(), respuesta.ofertaId());
            assertNull(respuesta.pedidoId());
            assertEquals("Tomate chonto", respuesta.producto());
            assertEquals("María Gómez", respuesta.productorNombre());
            assertEquals("Finca El Roble", respuesta.fincaNombre());
        }

        @Test
        @DisplayName("US-19 Regla — una oferta OTRO muestra el texto de productoOtro como producto")
        void testRegistrarContactoProductoOtro() {
            oferta.setProducto(Producto.OTRO);
            oferta.setProductoOtro("Lulo");
            simularCompradorYOferta();

            ContactoResponse respuesta = contactoService.registrarContacto(comprador.getId(),
                    request(CanalContacto.LLAMADA, null));

            assertEquals("Lulo", respuesta.producto());
        }

        @Test
        @DisplayName("US-19 Regla — una oferta agotada se registra igual")
        void testRegistrarContactoOfertaAgotada() {
            oferta.setActiva(false);
            simularCompradorYOferta();

            ContactoResponse respuesta = contactoService.registrarContacto(comprador.getId(),
                    request(CanalContacto.WHATSAPP, null));

            assertSame(oferta, registroGuardado().getOferta());
            assertEquals(oferta.getId(), respuesta.ofertaId());
        }

        @Test
        @DisplayName("US-19 Regla — asocia el pedido del comprador sobre la misma oferta")
        void testRegistrarContactoConPedido() {
            simularCompradorYOferta();
            Pedido pedido = pedidoDe(comprador, oferta);

            ContactoResponse respuesta = contactoService.registrarContacto(comprador.getId(),
                    request(CanalContacto.WHATSAPP, pedido.getId()));

            assertSame(pedido, registroGuardado().getPedido());
            assertEquals(pedido.getId(), respuesta.pedidoId());
        }

        @Test
        @DisplayName("US-19 Regla — un usuario inexistente responde 404 sin consultar la oferta")
        void testRegistrarContactoCompradorInexistente() {
            UUID desconocido = UUID.randomUUID();
            when(usuarioRepository.findById(desconocido)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> contactoService.registrarContacto(desconocido, request(CanalContacto.WHATSAPP, null)));

            assertEquals(ContactoService.MENSAJE_PERFIL_NO_ENCONTRADO, ex.getMessage());
            verifyNoInteractions(ofertaRepository, registroContactoRepository);
        }

        @Test
        @DisplayName("US-19 Regla — un usuario sin rol COMPRADOR responde 404")
        void testRegistrarContactoUsuarioNoComprador() {
            when(usuarioRepository.findById(productor.getId())).thenReturn(Optional.of(productor));

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> contactoService.registrarContacto(productor.getId(), request(CanalContacto.WHATSAPP, null)));

            assertEquals(ContactoService.MENSAJE_PERFIL_NO_ENCONTRADO, ex.getMessage());
            verifyNoInteractions(registroContactoRepository);
        }

        @Test
        @DisplayName("US-19 Regla — una oferta inexistente responde 404")
        void testRegistrarContactoOfertaInexistente() {
            when(usuarioRepository.findById(comprador.getId())).thenReturn(Optional.of(comprador));
            when(ofertaRepository.findById(oferta.getId())).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> contactoService.registrarContacto(comprador.getId(), request(CanalContacto.WHATSAPP, null)));

            assertEquals(ContactoService.MENSAJE_OFERTA_NO_ENCONTRADA, ex.getMessage());
            verifyNoInteractions(registroContactoRepository);
        }

        @Test
        @DisplayName("US-19 Regla — WhatsApp apagado por el productor responde 422")
        void testRegistrarContactoWhatsappApagado() {
            productor.setCanalWhatsappHabilitado(false);
            simularCompradorYOferta();

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> contactoService.registrarContacto(comprador.getId(), request(CanalContacto.WHATSAPP, null)));

            assertEquals(ContactoService.MENSAJE_CANAL_NO_HABILITADO, ex.getMessage());
            verify(registroContactoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("US-19 Regla — WhatsApp habilitado sin número responde 422")
        void testRegistrarContactoWhatsappSinNumero() {
            productor.setWhatsapp("   ");
            simularCompradorYOferta();

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> contactoService.registrarContacto(comprador.getId(), request(CanalContacto.WHATSAPP, null)));

            assertEquals(ContactoService.MENSAJE_CANAL_NO_HABILITADO, ex.getMessage());
        }

        @Test
        @DisplayName("US-19 Regla — llamada apagada por el productor responde 422 aunque WhatsApp esté disponible")
        void testRegistrarContactoLlamadaApagada() {
            productor.setCanalLlamadaHabilitado(false);
            simularCompradorYOferta();

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> contactoService.registrarContacto(comprador.getId(), request(CanalContacto.LLAMADA, null)));

            assertEquals(ContactoService.MENSAJE_CANAL_NO_HABILITADO, ex.getMessage());
            verify(registroContactoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("US-19 Regla — llamada habilitada sin teléfono responde 422")
        void testRegistrarContactoLlamadaSinNumero() {
            productor.setTelefono(null);
            simularCompradorYOferta();

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> contactoService.registrarContacto(comprador.getId(), request(CanalContacto.LLAMADA, null)));

            assertEquals(ContactoService.MENSAJE_CANAL_NO_HABILITADO, ex.getMessage());
        }

        @Test
        @DisplayName("US-19 Regla — el canal se valida antes que el pedido")
        void testRegistrarContactoCanalAntesQuePedido() {
            productor.setCanalWhatsappHabilitado(false);
            simularCompradorYOferta();

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> contactoService.registrarContacto(comprador.getId(),
                            request(CanalContacto.WHATSAPP, UUID.randomUUID())));

            assertEquals(ContactoService.MENSAJE_CANAL_NO_HABILITADO, ex.getMessage());
            verifyNoInteractions(pedidoRepository);
        }

        @Test
        @DisplayName("US-19 Regla — un pedido inexistente responde 422 con el mensaje de pedido")
        void testRegistrarContactoPedidoInexistente() {
            simularCompradorYOferta();
            UUID pedidoId = UUID.randomUUID();
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.empty());

            assertPedidoRechazado(pedidoId);
        }

        @Test
        @DisplayName("US-19 Regla — un pedido de otro comprador responde 422 con el mismo mensaje")
        void testRegistrarContactoPedidoDeOtroComprador() {
            simularCompradorYOferta();
            Usuario otroComprador = new Usuario();
            otroComprador.setId(UUID.randomUUID());
            otroComprador.setRol(Rol.COMPRADOR);
            Pedido pedido = pedidoDe(otroComprador, oferta);

            assertPedidoRechazado(pedido.getId());
        }

        @Test
        @DisplayName("US-19 Regla — un pedido de otra oferta responde 422 con el mismo mensaje")
        void testRegistrarContactoPedidoDeOtraOferta() {
            simularCompradorYOferta();
            Oferta otraOferta = new Oferta();
            otraOferta.setId(UUID.randomUUID());
            Pedido pedido = pedidoDe(comprador, otraOferta);

            assertPedidoRechazado(pedido.getId());
        }
    }

    @Nested
    @DisplayName("Historial de contactos")
    class Historial {

        private RegistroContacto registro(CanalContacto canal) {
            RegistroContacto registro = new RegistroContacto();
            registro.setId(UUID.randomUUID());
            registro.setComprador(comprador);
            registro.setOferta(oferta);
            registro.setCanal(canal);
            registro.setFechaContacto(FECHA_CONTACTO);
            return registro;
        }

        @Test
        @DisplayName("US-19 Esc 2 — devuelve los contactos del comprador en el orden del repositorio")
        void testListarMisContactos() {
            RegistroContacto reciente = registro(CanalContacto.LLAMADA);
            RegistroContacto antiguo = registro(CanalContacto.WHATSAPP);
            when(registroContactoRepository.findByCompradorIdOrderByFechaContactoDescIdAsc(comprador.getId()))
                    .thenReturn(List.of(reciente, antiguo));

            List<ContactoResponse> contactos = contactoService.listarMisContactos(comprador.getId());

            assertEquals(List.of(reciente.getId(), antiguo.getId()), contactos.stream().map(ContactoResponse::id).toList());
            assertEquals(CanalContacto.LLAMADA, contactos.get(0).canal());
            assertEquals("Tomate chonto", contactos.get(0).producto());
        }

        @Test
        @DisplayName("US-19 Esc 3 — sin contactos devuelve una lista vacía")
        void testListarMisContactosVacio() {
            when(registroContactoRepository.findByCompradorIdOrderByFechaContactoDescIdAsc(comprador.getId()))
                    .thenReturn(List.of());

            assertTrue(contactoService.listarMisContactos(comprador.getId()).isEmpty());
        }
    }
}
