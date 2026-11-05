// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: Reglas del checkout con reloj fijo: oferta, cantidad, fechas, modalidad, total y permisos
// ============================================================
package com.campored.backend.service;

import com.campored.backend.dto.CrearPedidoRequest;
import com.campored.backend.dto.PedidoResponse;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.EstadoPedido;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Jornada;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Negocio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Pedido;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.TipoNegocio;
import com.campored.backend.entity.UnidadMedida;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.AccesoDenegadoException;
import com.campored.backend.exception.GlobalExceptionHandler;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.PedidoRepository;
import com.campored.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PedidoService - Checkout y consulta de pedidos")
class PedidoServiceTest {

    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
    // 10:00 en Bogotá del 5 de noviembre de 2026
    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private PedidoService pedidoService;

    private Usuario comprador;
    private Usuario productor;
    private Oferta oferta;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pedidoService = new PedidoService(pedidoRepository, ofertaRepository, usuarioRepository,
                Clock.fixed(AHORA, ZONA_COLOMBIA));

        productor = new Usuario();
        productor.setId(UUID.randomUUID());
        productor.setNombre("María Gómez");
        productor.setRol(Rol.PRODUCTOR);
        productor.setWhatsapp("+573109876543");
        productor.setTelefono("+573001234567");
        productor.setCanalWhatsappHabilitado(true);
        productor.setCanalLlamadaHabilitado(false);
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
        comprador.setWhatsapp("+573201112233");
        Negocio negocio = new Negocio();
        negocio.setNombreNegocio("Restaurante El Fogón");
        negocio.setTipoNegocio(TipoNegocio.RESTAURANTE);
        negocio.setDireccion("Calle 49 # 50-21");
        negocio.setMunicipio(Municipio.RIONEGRO);
        comprador.asignarNegocio(negocio);

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

    private CrearPedidoRequest.CrearPedidoRequestBuilder requestValido() {
        return CrearPedidoRequest.builder()
                .ofertaId(oferta.getId())
                .cantidadSolicitada(new BigDecimal("120.00"))
                .direccionEntrega("  Calle 49 # 50-21, local 3  ")
                .municipioEntrega("Rionegro")
                .fechaPreferida(HOY.plusDays(3))
                .jornada(Jornada.MANANA)
                .modalidadEntrega(ModalidadLogistica.DESPACHO_PRODUCTOR)
                .notasAcceso("  Entrada por la puerta de carga  ");
    }

    private void simularCompradorYOferta() {
        when(usuarioRepository.findById(comprador.getId())).thenReturn(Optional.of(comprador));
        when(ofertaRepository.findById(oferta.getId())).thenReturn(Optional.of(oferta));
        when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(invocacion -> {
            Pedido pedido = invocacion.getArgument(0);
            pedido.setId(UUID.randomUUID());
            pedido.setFechaCreacion(LocalDateTime.of(2026, 11, 5, 10, 0));
            return pedido;
        });
    }

    private Pedido pedidoGuardado() {
        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Map<String, String> erroresDe(CrearPedidoRequest request) {
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> pedidoService.crearPedido(comprador.getId(), request));
        assertEquals(GlobalExceptionHandler.MENSAJE_CAMPOS_INVALIDOS, ex.getMessage());
        verify(pedidoRepository, never()).saveAndFlush(any());
        return ex.getErrores();
    }

    @Nested
    @DisplayName("Crear pedido")
    class CrearPedido {

        @Test
        @DisplayName("US-18 Esc 1 — crea el pedido SOLICITADO con el comprador, la oferta y los datos de entrega")
        void testCrearPedidoExitoso() {
            simularCompradorYOferta();

            PedidoResponse respuesta = pedidoService.crearPedido(comprador.getId(), requestValido().build());

            Pedido guardado = pedidoGuardado();
            assertSame(comprador, guardado.getComprador());
            assertSame(oferta, guardado.getOferta());
            assertEquals(EstadoPedido.SOLICITADO, guardado.getEstado());
            assertEquals(new BigDecimal("120.00"), guardado.getCantidadSolicitada());
            assertEquals("Calle 49 # 50-21, local 3", guardado.getDireccionEntrega());
            assertEquals(Municipio.RIONEGRO, guardado.getMunicipioEntrega());
            assertEquals(HOY.plusDays(3), guardado.getFechaPreferida());
            assertEquals(Jornada.MANANA, guardado.getJornada());
            assertEquals(ModalidadLogistica.DESPACHO_PRODUCTOR, guardado.getModalidadEntrega());
            assertEquals("Entrada por la puerta de carga", guardado.getNotasAcceso());

            assertEquals(guardado.getId(), respuesta.id());
            assertEquals(EstadoPedido.SOLICITADO, respuesta.estado());
        }

        @Test
        @DisplayName("US-18 Esc 4 — la respuesta trae los datos del resumen: producto, precios, entrega y contacto")
        void testCrearPedidoRespuestaConResumen() {
            simularCompradorYOferta();

            PedidoResponse respuesta = pedidoService.crearPedido(comprador.getId(), requestValido().build());

            assertEquals(new BigDecimal("120.00"), respuesta.cantidadSolicitada());
            assertEquals(new BigDecimal("3500.00"), respuesta.precioUnitario());
            assertEquals(new BigDecimal("420000.00"), respuesta.precioTotalEstimado());
            assertEquals("Calle 49 # 50-21, local 3", respuesta.direccionEntrega());
            assertEquals(Municipio.RIONEGRO, respuesta.municipioEntrega());
            assertEquals(HOY.plusDays(3), respuesta.fechaPreferida());
            assertEquals(Jornada.MANANA, respuesta.jornada());
            assertEquals(ModalidadLogistica.DESPACHO_PRODUCTOR, respuesta.modalidadEntrega());
            assertEquals("Entrada por la puerta de carga", respuesta.notasAcceso());
            assertEquals(LocalDateTime.of(2026, 11, 5, 10, 0), respuesta.fechaCreacion());

            assertEquals(oferta.getId(), respuesta.oferta().id());
            assertEquals(Producto.TOMATE_CHONTO, respuesta.oferta().producto());
            assertNull(respuesta.oferta().productoOtro());
            assertEquals(UnidadMedida.KILOGRAMO, respuesta.oferta().unidadMedida());
            assertEquals("Finca El Roble", respuesta.oferta().fincaNombre());
            assertEquals(Municipio.SONSON, respuesta.oferta().municipio());

            // WhatsApp habilitado con número; llamada apagada aunque haya teléfono
            assertEquals("María Gómez", respuesta.productor().nombreCompleto());
            assertTrue(respuesta.productor().whatsappDisponible());
            assertFalse(respuesta.productor().llamadaDisponible());
            assertEquals("+573109876543", respuesta.productor().whatsappContacto());
            assertNull(respuesta.productor().telefonoContacto());

            assertEquals("Laura Restrepo", respuesta.comprador().nombre());
            assertEquals("Restaurante El Fogón", respuesta.comprador().nombreNegocio());
            assertEquals("+573201112233", respuesta.comprador().whatsapp());
            assertNull(respuesta.comprador().telefono());
        }

        @Test
        @DisplayName("US-18 Regla — el precio unitario se copia de la oferta y el total se redondea a 2 decimales")
        void testCrearPedidoCalculaTotal() {
            oferta.setPrecioUnitario(new BigDecimal("3333.33"));
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(),
                    requestValido().cantidadSolicitada(new BigDecimal("1.25")).build());

            Pedido guardado = pedidoGuardado();
            assertEquals(new BigDecimal("3333.33"), guardado.getPrecioUnitario());
            // 1.25 × 3333.33 = 4166.6625 → 4166.66
            assertEquals(new BigDecimal("4166.66"), guardado.getPrecioTotalEstimado());
            assertEquals(2, guardado.getPrecioTotalEstimado().scale());
        }

        @Test
        @DisplayName("US-18 Regla — el total redondea la mitad hacia arriba")
        void testCrearPedidoTotalRedondeaHaciaArriba() {
            oferta.setPrecioUnitario(new BigDecimal("1000.01"));
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(),
                    requestValido().cantidadSolicitada(new BigDecimal("0.50")).build());

            // 0.50 × 1000.01 = 500.0050 → 500.01
            assertEquals(new BigDecimal("500.01"), pedidoGuardado().getPrecioTotalEstimado());
        }

        @Test
        @DisplayName("US-18 Regla — el pedido no descuenta la cantidad disponible de la oferta")
        void testCrearPedidoNoDescuentaInventario() {
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(), requestValido().build());

            assertEquals(new BigDecimal("250.00"), oferta.getCantidadDisponible());
            assertTrue(oferta.isActiva());
            verify(ofertaRepository, never()).save(any());
        }

        @Test
        @DisplayName("US-18 Regla — pedir exactamente la cantidad disponible es válido")
        void testCrearPedidoCantidadIgualADisponible() {
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(),
                    requestValido().cantidadSolicitada(new BigDecimal("250.00")).build());

            assertEquals(new BigDecimal("250.00"), pedidoGuardado().getCantidadSolicitada());
        }

        @Test
        @DisplayName("US-18 Regla — las notas vacías o con solo espacios se guardan como null")
        void testCrearPedidoNotasVacias() {
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(), requestValido().notasAcceso("   ").build());

            assertNull(pedidoGuardado().getNotasAcceso());
        }

        @Test
        @DisplayName("US-18 Regla — acepta el código del municipio además del nombre visible")
        void testCrearPedidoMunicipioPorCodigo() {
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(), requestValido().municipioEntrega("SAN_VICENTE_FERRER").build());

            assertEquals(Municipio.SAN_VICENTE_FERRER, pedidoGuardado().getMunicipioEntrega());
        }

        @Test
        @DisplayName("US-18 Regla — un usuario inexistente responde 404 sin consultar la oferta")
        void testCrearPedidoCompradorInexistente() {
            UUID desconocido = UUID.randomUUID();
            when(usuarioRepository.findById(desconocido)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> pedidoService.crearPedido(desconocido, requestValido().build()));

            assertEquals(PedidoService.MENSAJE_PERFIL_NO_ENCONTRADO, ex.getMessage());
            verify(ofertaRepository, never()).findById(any());
            verify(pedidoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("US-18 Regla — un usuario sin rol COMPRADOR responde 404")
        void testCrearPedidoUsuarioNoComprador() {
            when(usuarioRepository.findById(productor.getId())).thenReturn(Optional.of(productor));

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> pedidoService.crearPedido(productor.getId(), requestValido().build()));

            assertEquals(PedidoService.MENSAJE_PERFIL_NO_ENCONTRADO, ex.getMessage());
            verify(pedidoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("US-18 Regla — una oferta inexistente responde 404")
        void testCrearPedidoOfertaInexistente() {
            when(usuarioRepository.findById(comprador.getId())).thenReturn(Optional.of(comprador));
            when(ofertaRepository.findById(oferta.getId())).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> pedidoService.crearPedido(comprador.getId(), requestValido().build()));

            assertEquals(PedidoService.MENSAJE_OFERTA_NO_ENCONTRADA, ex.getMessage());
            verify(pedidoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("US-18 Regla — una oferta agotada responde 422 antes de validar los campos")
        void testCrearPedidoOfertaAgotada() {
            oferta.setActiva(false);
            simularCompradorYOferta();
            CrearPedidoRequest request = requestValido()
                    .cantidadSolicitada(new BigDecimal("999"))
                    .fechaPreferida(HOY.minusDays(1))
                    .build();

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> pedidoService.crearPedido(comprador.getId(), request));

            assertEquals(PedidoService.MENSAJE_OFERTA_AGOTADA, ex.getMessage());
            verify(pedidoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("US-18 Regla — un municipio fuera del catálogo responde en errores.municipioEntrega")
        void testCrearPedidoMunicipioInvalido() {
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().municipioEntrega("Bogotá").build());

            assertEquals(Map.of("municipioEntrega", PedidoService.MENSAJE_MUNICIPIO_INVALIDO), errores);
        }

        @Test
        @DisplayName("US-18 Esc 5 — una cantidad mayor a la disponible responde en errores.cantidadSolicitada")
        void testCrearPedidoCantidadMayorADisponible() {
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().cantidadSolicitada(new BigDecimal("250.01")).build());

            assertEquals(Map.of("cantidadSolicitada", PedidoService.MENSAJE_CANTIDAD_SUPERA_DISPONIBLE), errores);
        }

        @Test
        @DisplayName("US-18 Regla — una fecha preferida anterior a hoy responde en errores.fechaPreferida")
        void testCrearPedidoFechaPasada() {
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().fechaPreferida(HOY.minusDays(1)).build());

            assertEquals(Map.of("fechaPreferida", PedidoService.MENSAJE_FECHA_ANTERIOR_A_HOY), errores);
        }

        @Test
        @DisplayName("US-18 Regla — la fecha preferida de hoy (hora Colombia) es válida")
        void testCrearPedidoFechaHoy() {
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(), requestValido().fechaPreferida(HOY).build());

            assertEquals(HOY, pedidoGuardado().getFechaPreferida());
        }

        @Test
        @DisplayName("US-18 Regla — en una oferta EN_CRECIMIENTO la fecha no puede ser anterior a la cosecha")
        void testCrearPedidoFechaAntesDeCosecha() {
            oferta.setEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO);
            oferta.setFechaEstimadaCosecha(HOY.plusDays(10));
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().fechaPreferida(HOY.plusDays(9)).build());

            assertEquals(Map.of("fechaPreferida", PedidoService.MENSAJE_FECHA_ANTERIOR_A_COSECHA), errores);
        }

        @Test
        @DisplayName("US-18 Regla — en una oferta EN_CRECIMIENTO la fecha de cosecha misma es válida")
        void testCrearPedidoFechaIgualACosecha() {
            oferta.setEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO);
            oferta.setFechaEstimadaCosecha(HOY.plusDays(10));
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(), requestValido().fechaPreferida(HOY.plusDays(10)).build());

            assertEquals(HOY.plusDays(10), pedidoGuardado().getFechaPreferida());
        }

        @Test
        @DisplayName("US-18 Regla — una fecha pasada en una oferta EN_CRECIMIENTO reporta solo el error de hoy")
        void testCrearPedidoFechaPasadaEnCrecimiento() {
            oferta.setEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO);
            oferta.setFechaEstimadaCosecha(HOY.plusDays(10));
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().fechaPreferida(HOY.minusDays(1)).build());

            assertEquals(Map.of("fechaPreferida", PedidoService.MENSAJE_FECHA_ANTERIOR_A_HOY), errores);
        }

        @Test
        @DisplayName("US-18 Regla — en una oferta EN_CRECIMIENTO con la cosecha vencida basta con no pedir antes de hoy")
        void testCrearPedidoCosechaVencida() {
            oferta.setEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO);
            oferta.setFechaEstimadaCosecha(HOY.minusDays(5));
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(), requestValido().fechaPreferida(HOY).build());

            assertEquals(HOY, pedidoGuardado().getFechaPreferida());
        }

        @Test
        @DisplayName("US-18 Regla — si la oferta es AMBAS la modalidad de entrega es obligatoria")
        void testCrearPedidoAmbasSinModalidad() {
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().modalidadEntrega(null).build());

            assertEquals(Map.of("modalidadEntrega", PedidoService.MENSAJE_MODALIDAD_REQUERIDA), errores);
        }

        @Test
        @DisplayName("US-18 Regla — si la oferta es AMBAS el comprador puede elegir recogida en finca")
        void testCrearPedidoAmbasEligeRecogida() {
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(),
                    requestValido().modalidadEntrega(ModalidadLogistica.RECOGIDA_FINCA).build());

            assertEquals(ModalidadLogistica.RECOGIDA_FINCA, pedidoGuardado().getModalidadEntrega());
        }

        @Test
        @DisplayName("US-18 Regla — AMBAS nunca se acepta como modalidad del pedido")
        void testCrearPedidoModalidadAmbasRechazada() {
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().modalidadEntrega(ModalidadLogistica.AMBAS).build());

            assertEquals(Map.of("modalidadEntrega", PedidoService.MENSAJE_MODALIDAD_NO_HABILITADA), errores);
        }

        @Test
        @DisplayName("US-18 Regla — AMBAS se rechaza también si la oferta tiene una sola modalidad")
        void testCrearPedidoModalidadAmbasEnOfertaSimple() {
            oferta.setModalidadLogistica(ModalidadLogistica.RECOGIDA_FINCA);
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(requestValido().modalidadEntrega(ModalidadLogistica.AMBAS).build());

            assertEquals(Map.of("modalidadEntrega", PedidoService.MENSAJE_MODALIDAD_NO_HABILITADA), errores);
        }

        @Test
        @DisplayName("US-18 Regla — si la oferta no es AMBAS y no llega modalidad se fija la de la oferta")
        void testCrearPedidoModalidadFijadaPorOferta() {
            oferta.setModalidadLogistica(ModalidadLogistica.RECOGIDA_FINCA);
            simularCompradorYOferta();

            PedidoResponse respuesta = pedidoService.crearPedido(comprador.getId(),
                    requestValido().modalidadEntrega(null).build());

            assertEquals(ModalidadLogistica.RECOGIDA_FINCA, pedidoGuardado().getModalidadEntrega());
            assertEquals(ModalidadLogistica.RECOGIDA_FINCA, respuesta.modalidadEntrega());
        }

        @Test
        @DisplayName("US-18 Regla — la modalidad igual a la única de la oferta es válida")
        void testCrearPedidoModalidadIgualALaOferta() {
            oferta.setModalidadLogistica(ModalidadLogistica.DESPACHO_PRODUCTOR);
            simularCompradorYOferta();

            pedidoService.crearPedido(comprador.getId(), requestValido().build());

            assertEquals(ModalidadLogistica.DESPACHO_PRODUCTOR, pedidoGuardado().getModalidadEntrega());
        }

        @Test
        @DisplayName("US-18 Regla — una modalidad que la oferta no habilitó responde en errores.modalidadEntrega")
        void testCrearPedidoModalidadNoHabilitada() {
            oferta.setModalidadLogistica(ModalidadLogistica.RECOGIDA_FINCA);
            simularCompradorYOferta();

            Map<String, String> errores = erroresDe(
                    requestValido().modalidadEntrega(ModalidadLogistica.DESPACHO_PRODUCTOR).build());

            assertEquals(Map.of("modalidadEntrega", PedidoService.MENSAJE_MODALIDAD_NO_HABILITADA), errores);
        }

        @Test
        @DisplayName("US-18 Regla — acumula todos los errores de negocio en una sola respuesta y en orden de formulario")
        void testCrearPedidoAcumulaErrores() {
            oferta.setModalidadLogistica(ModalidadLogistica.RECOGIDA_FINCA);
            simularCompradorYOferta();
            CrearPedidoRequest request = requestValido()
                    .municipioEntrega("Bogotá")
                    .cantidadSolicitada(new BigDecimal("300"))
                    .fechaPreferida(HOY.minusDays(2))
                    .modalidadEntrega(ModalidadLogistica.DESPACHO_PRODUCTOR)
                    .build();

            Map<String, String> errores = erroresDe(request);

            assertEquals(List.of("municipioEntrega", "cantidadSolicitada", "fechaPreferida", "modalidadEntrega"),
                    List.copyOf(errores.keySet()));
            assertEquals(PedidoService.MENSAJE_MUNICIPIO_INVALIDO, errores.get("municipioEntrega"));
            assertEquals(PedidoService.MENSAJE_CANTIDAD_SUPERA_DISPONIBLE, errores.get("cantidadSolicitada"));
            assertEquals(PedidoService.MENSAJE_FECHA_ANTERIOR_A_HOY, errores.get("fechaPreferida"));
            assertEquals(PedidoService.MENSAJE_MODALIDAD_NO_HABILITADA, errores.get("modalidadEntrega"));
        }
    }

    @Nested
    @DisplayName("Consultar pedido")
    class ObtenerPedido {

        private Pedido pedido;

        @BeforeEach
        void crearPedido() {
            pedido = new Pedido();
            pedido.setId(UUID.randomUUID());
            pedido.setOferta(oferta);
            pedido.setComprador(comprador);
            pedido.setCantidadSolicitada(new BigDecimal("10.00"));
            pedido.setPrecioUnitario(new BigDecimal("3500.00"));
            pedido.setPrecioTotalEstimado(new BigDecimal("35000.00"));
            pedido.setDireccionEntrega("Calle 49 # 50-21");
            pedido.setMunicipioEntrega(Municipio.RIONEGRO);
            pedido.setFechaPreferida(HOY.plusDays(2));
            pedido.setJornada(Jornada.TARDE);
            pedido.setModalidadEntrega(ModalidadLogistica.RECOGIDA_FINCA);
            when(pedidoRepository.findDetalleById(pedido.getId())).thenReturn(Optional.of(pedido));
        }

        @Test
        @DisplayName("US-18 Regla — el comprador dueño ve su pedido")
        void testObtenerPedidoComprador() {
            PedidoResponse respuesta = pedidoService.obtenerPedido(pedido.getId(), comprador.getId());

            assertEquals(pedido.getId(), respuesta.id());
            assertEquals(EstadoPedido.SOLICITADO, respuesta.estado());
            assertEquals(Jornada.TARDE, respuesta.jornada());
            assertEquals("+573109876543", respuesta.productor().whatsappContacto());
        }

        @Test
        @DisplayName("US-18 Regla — el productor de la oferta ve el pedido con el contacto del comprador")
        void testObtenerPedidoProductor() {
            PedidoResponse respuesta = pedidoService.obtenerPedido(pedido.getId(), productor.getId());

            assertEquals(pedido.getId(), respuesta.id());
            assertEquals("Laura Restrepo", respuesta.comprador().nombre());
            assertEquals("+573201112233", respuesta.comprador().whatsapp());
        }

        @Test
        @DisplayName("US-18 Regla — un comprador sin negocio registrado devuelve nombreNegocio null")
        void testObtenerPedidoCompradorSinNegocio() {
            Usuario sinNegocio = new Usuario();
            sinNegocio.setId(UUID.randomUUID());
            sinNegocio.setNombre("Pedro Ruiz");
            sinNegocio.setRol(Rol.COMPRADOR);
            pedido.setComprador(sinNegocio);

            PedidoResponse respuesta = pedidoService.obtenerPedido(pedido.getId(), sinNegocio.getId());

            assertEquals("Pedro Ruiz", respuesta.comprador().nombre());
            assertNull(respuesta.comprador().nombreNegocio());
        }

        @Test
        @DisplayName("US-18 Regla — un usuario que no es parte del pedido recibe 403")
        void testObtenerPedidoAjeno() {
            AccesoDenegadoException ex = assertThrows(AccesoDenegadoException.class,
                    () -> pedidoService.obtenerPedido(pedido.getId(), UUID.randomUUID()));

            assertEquals(PedidoService.MENSAJE_PEDIDO_AJENO, ex.getMessage());
        }

        @Test
        @DisplayName("US-18 Regla — un pedido inexistente responde 404")
        void testObtenerPedidoInexistente() {
            UUID id = UUID.randomUUID();
            when(pedidoRepository.findDetalleById(id)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> pedidoService.obtenerPedido(id, comprador.getId()));

            assertEquals(PedidoService.MENSAJE_PEDIDO_NO_ENCONTRADO, ex.getMessage());
        }
    }
}
