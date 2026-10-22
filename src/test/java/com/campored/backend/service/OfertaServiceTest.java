package com.campored.backend.service;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.dto.FiltroCatalogoOfertas;
import com.campored.backend.dto.OfertaCatalogoResponse;
import com.campored.backend.dto.OfertaDetalleResponse;
import com.campored.backend.dto.OfertaResponse;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.UnidadMedida;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OfertaService - Publicación de ofertas del productor")
class OfertaServiceTest {

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private OfertaService ofertaService;

    private UUID productorId;
    private Usuario productor;
    private Finca finca;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productorId = UUID.randomUUID();
        finca = new Finca();
        finca.setId(UUID.randomUUID());
        finca.setNombreFinca("Finca El Roble");
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("La Honda");
        productor = new Usuario();
        productor.setId(productorId);
        productor.setRol(Rol.PRODUCTOR);
        productor.asignarFinca(finca);
    }

    private CrearOfertaRequest.CrearOfertaRequestBuilder requestBase() {
        return CrearOfertaRequest.builder()
                .producto(Producto.TOMATE_CHONTO)
                .descripcion("  Tomate chonto de primera  ")
                .cantidadDisponible(new BigDecimal("250.00"))
                .unidadMedida(UnidadMedida.KILOGRAMO)
                .precioUnitario(new BigDecimal("3500"))
                .estadoCultivo(EstadoCultivo.EN_CRECIMIENTO)
                .fechaEstimadaCosecha(LocalDate.now().plusDays(30))
                .modalidadLogistica(ModalidadLogistica.AMBAS);
    }

    private void simularGuardado() {
        when(ofertaRepository.save(any(Oferta.class))).thenAnswer(invocacion -> {
            Oferta oferta = invocacion.getArgument(0);
            oferta.setId(UUID.randomUUID());
            return oferta;
        });
    }

    @Test
    @DisplayName("Debe crear la oferta con el productor y la finca del usuario autenticado")
    void testCrearOfertaExitoso() {
        CrearOfertaRequest request = requestBase().build();
        when(usuarioRepository.findById(productorId)).thenReturn(Optional.of(productor));
        simularGuardado();

        OfertaResponse respuesta = ofertaService.crearOferta(productorId, request);

        ArgumentCaptor<Oferta> captor = ArgumentCaptor.forClass(Oferta.class);
        verify(ofertaRepository).save(captor.capture());
        Oferta guardada = captor.getValue();
        assertSame(productor, guardada.getProductor());
        assertSame(finca, guardada.getFinca());
        assertTrue(guardada.isActiva());
        assertEquals("Tomate chonto de primera", guardada.getDescripcion());
        assertEquals(request.getFechaEstimadaCosecha(), guardada.getFechaEstimadaCosecha());

        assertEquals(guardada.getId(), respuesta.getId());
        assertEquals(Producto.TOMATE_CHONTO, respuesta.getProducto());
        assertEquals(new BigDecimal("3500"), respuesta.getPrecioUnitario());
        assertEquals(ModalidadLogistica.AMBAS, respuesta.getModalidadLogistica());
        assertEquals(finca.getId(), respuesta.getFincaId());
        assertEquals("Finca El Roble", respuesta.getFincaNombre());
        assertEquals(Municipio.SONSON, respuesta.getMunicipio());
        assertTrue(respuesta.isActiva());
    }

    @Test
    @DisplayName("Debe guardar productoOtro como null cuando el producto es del catálogo")
    void testCrearOfertaDescartaProductoOtro() {
        CrearOfertaRequest request = requestBase().productoOtro("Texto que no aplica").descripcion("   ").build();
        when(usuarioRepository.findById(productorId)).thenReturn(Optional.of(productor));
        simularGuardado();

        OfertaResponse respuesta = ofertaService.crearOferta(productorId, request);

        assertNull(respuesta.getProductoOtro());
        assertNull(respuesta.getDescripcion());
    }

    @Test
    @DisplayName("Debe guardar productoOtro sin espacios cuando el producto es Otro")
    void testCrearOfertaProductoOtro() {
        CrearOfertaRequest request = requestBase().producto(Producto.OTRO).productoOtro("  Lulo  ").build();
        when(usuarioRepository.findById(productorId)).thenReturn(Optional.of(productor));
        simularGuardado();

        OfertaResponse respuesta = ofertaService.crearOferta(productorId, request);

        assertEquals(Producto.OTRO, respuesta.getProducto());
        assertEquals("Lulo", respuesta.getProductoOtro());
    }

    @Test
    @DisplayName("Debe guardar la fecha de cosecha como null cuando el producto está disponible")
    void testCrearOfertaDisponibleDescartaFecha() {
        CrearOfertaRequest request = requestBase().estadoCultivo(EstadoCultivo.DISPONIBLE).build();
        when(usuarioRepository.findById(productorId)).thenReturn(Optional.of(productor));
        simularGuardado();

        OfertaResponse respuesta = ofertaService.crearOferta(productorId, request);

        ArgumentCaptor<Oferta> captor = ArgumentCaptor.forClass(Oferta.class);
        verify(ofertaRepository).save(captor.capture());
        assertNull(captor.getValue().getFechaEstimadaCosecha());
        assertNull(respuesta.getFechaEstimadaCosecha());
        assertEquals(EstadoCultivo.DISPONIBLE, respuesta.getEstadoCultivo());
    }

    @Test
    @DisplayName("Debe lanzar ReglaNegocioException si el productor no tiene finca")
    void testCrearOfertaProductorSinFinca() {
        Usuario sinFinca = new Usuario();
        sinFinca.setId(productorId);
        sinFinca.setRol(Rol.PRODUCTOR);
        when(usuarioRepository.findById(productorId)).thenReturn(Optional.of(sinFinca));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> ofertaService.crearOferta(productorId, requestBase().build()));

        assertEquals(OfertaService.MENSAJE_PRODUCTOR_SIN_FINCA, ex.getMessage());
        verify(ofertaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si el usuario del token no existe")
    void testCrearOfertaProductorInexistente() {
        when(usuarioRepository.findById(productorId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> ofertaService.crearOferta(productorId, requestBase().build()));
        verify(ofertaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si el usuario del token no es productor")
    void testCrearOfertaUsuarioNoProductor() {
        productor.setRol(Rol.COMPRADOR);
        when(usuarioRepository.findById(productorId)).thenReturn(Optional.of(productor));

        assertThrows(ResourceNotFoundException.class,
                () -> ofertaService.crearOferta(productorId, requestBase().build()));
        verify(ofertaRepository, never()).save(any());
    }

    private Oferta ofertaGuardada(Producto producto) {
        Oferta oferta = new Oferta();
        oferta.setId(UUID.randomUUID());
        oferta.setProductor(productor);
        oferta.setFinca(finca);
        oferta.setProducto(producto);
        oferta.setCantidadDisponible(new BigDecimal("40"));
        oferta.setUnidadMedida(UnidadMedida.ARROBA);
        oferta.setPrecioUnitario(new BigDecimal("52000"));
        oferta.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        oferta.setModalidadLogistica(ModalidadLogistica.DESPACHO_PRODUCTOR);
        return oferta;
    }

    @Test
    @DisplayName("Debe listar las ofertas del productor mapeadas y en el orden del repositorio")
    void testListarMisOfertas() {
        Oferta reciente = ofertaGuardada(Producto.CAFE_PERGAMINO);
        Oferta antigua = ofertaGuardada(Producto.MORA_CASTILLA);
        when(ofertaRepository.findByProductorIdOrderByFechaCreacionDesc(productorId))
                .thenReturn(List.of(reciente, antigua));

        List<OfertaResponse> respuesta = ofertaService.listarMisOfertas(productorId);

        assertEquals(2, respuesta.size());
        assertEquals(reciente.getId(), respuesta.get(0).getId());
        assertEquals(Producto.CAFE_PERGAMINO, respuesta.get(0).getProducto());
        assertEquals(UnidadMedida.ARROBA, respuesta.get(0).getUnidadMedida());
        assertEquals("Finca El Roble", respuesta.get(0).getFincaNombre());
        assertEquals(Municipio.SONSON, respuesta.get(0).getMunicipio());
        assertEquals(antigua.getId(), respuesta.get(1).getId());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("Debe devolver una lista vacía si el productor no tiene ofertas")
    void testListarMisOfertasSinOfertas() {
        when(ofertaRepository.findByProductorIdOrderByFechaCreacionDesc(productorId)).thenReturn(List.of());

        List<OfertaResponse> respuesta = ofertaService.listarMisOfertas(productorId);

        assertTrue(respuesta.isEmpty());
    }

    // ============================================================
    // FEATURE: US-11 / US-12 / US-13 — Catálogo y Detalle Públicos de Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Paginación, orden permitido y días restantes de cosecha con reloj fijo
    // ============================================================
    @Nested
    @DisplayName("Catálogo y detalle públicos")
    class CatalogoPublico {

        private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");
        // 10:00 en Bogotá del 22 de octubre de 2026
        private static final Instant AHORA = Instant.parse("2026-10-22T15:00:00Z");
        private static final LocalDate HOY = LocalDate.of(2026, 10, 22);

        private final FiltroCatalogoOfertas sinFiltros = new FiltroCatalogoOfertas(null, null, null, null);

        private OfertaService servicioConReloj(Instant instante) {
            return new OfertaService(ofertaRepository, usuarioRepository, Clock.fixed(instante, ZONA_COLOMBIA));
        }

        private Oferta ofertaEnCrecimiento(LocalDate fechaCosecha) {
            Oferta oferta = ofertaGuardada(Producto.TOMATE_CHONTO);
            oferta.setEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO);
            oferta.setFechaEstimadaCosecha(fechaCosecha);
            return oferta;
        }

        private Pageable consultarCatalogo(Pageable solicitada, List<Oferta> ofertas) {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            when(ofertaRepository.findAll(any(Specification.class), captor.capture()))
                    .thenAnswer(invocacion -> new PageImpl<>(ofertas, invocacion.getArgument(1), ofertas.size()));

            servicioConReloj(AHORA).listarCatalogo(sinFiltros, solicitada);
            return captor.getValue();
        }

        @Test
        @DisplayName("Debe ordenar por fecha de creación descendente si la petición no trae orden")
        void testListarCatalogoOrdenPorDefecto() {
            Pageable aplicada = consultarCatalogo(PageRequest.of(0, 20), List.of());

            Sort.Order porFecha = aplicada.getSort().getOrderFor("fechaCreacion");
            assertEquals(Sort.Direction.DESC, porFecha.getDirection());
            assertEquals("fechaCreacion", aplicada.getSort().iterator().next().getProperty());
            assertEquals(Sort.Direction.ASC, aplicada.getSort().getOrderFor("id").getDirection());
            assertEquals(20, aplicada.getPageSize());
        }

        @Test
        @DisplayName("Debe respetar un orden permitido enviado por el cliente")
        void testListarCatalogoOrdenPermitido() {
            Pageable aplicada = consultarCatalogo(
                    PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "precioUnitario")), List.of());

            assertEquals(Sort.Direction.ASC, aplicada.getSort().getOrderFor("precioUnitario").getDirection());
            assertNull(aplicada.getSort().getOrderFor("fechaCreacion"));
            assertEquals(2, aplicada.getPageNumber());
        }

        @Test
        @DisplayName("Debe limitar el tamaño de página a 50")
        void testListarCatalogoLimitaTamano() {
            Pageable aplicada = consultarCatalogo(PageRequest.of(0, 500), List.of());

            assertEquals(OfertaService.TAMANO_MAXIMO_PAGINA, aplicada.getPageSize());
        }

        @Test
        @DisplayName("Debe lanzar InvalidInputException si se ordena por un campo no permitido")
        void testListarCatalogoOrdenNoPermitido() {
            Pageable solicitada = PageRequest.of(0, 20, Sort.by("fechaCreacion").and(Sort.by("productor.correo")));

            InvalidInputException ex = assertThrows(InvalidInputException.class,
                    () -> servicioConReloj(AHORA).listarCatalogo(sinFiltros, solicitada));

            assertTrue(ex.getMessage().contains("productor.correo"));
            verifyNoInteractions(ofertaRepository);
        }

        @Test
        @DisplayName("Debe mapear cada oferta del catálogo con su finca pública y días restantes")
        void testListarCatalogoMapeaOfertas() {
            Oferta oferta = ofertaEnCrecimiento(HOY.plusDays(5));
            when(ofertaRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(oferta)));

            Page<OfertaCatalogoResponse> pagina = servicioConReloj(AHORA).listarCatalogo(
                    new FiltroCatalogoOfertas(Municipio.SONSON, EstadoCultivo.EN_CRECIMIENTO,
                            Producto.TOMATE_CHONTO, ModalidadLogistica.DESPACHO_PRODUCTOR),
                    PageRequest.of(0, 20));

            OfertaCatalogoResponse item = pagina.getContent().get(0);
            assertEquals(oferta.getId(), item.id());
            assertEquals(new BigDecimal("40"), item.cantidadDisponible());
            assertEquals(new BigDecimal("52000"), item.precioUnitario());
            assertEquals(5, item.diasRestantesCosecha());
            assertEquals(finca.getId(), item.finca().id());
            assertEquals("Finca El Roble", item.finca().nombre());
            assertEquals("La Honda", item.finca().vereda());
            assertEquals(Municipio.SONSON, item.finca().municipio());
            verifyNoInteractions(usuarioRepository);
        }

        @Test
        @DisplayName("Debe devolver el detalle con finca, municipio y solo el nombre del productor")
        void testObtenerDetalleExistente() {
            productor.setNombre("María Gómez");
            productor.setCorreo("maria@finca.com");
            Oferta oferta = ofertaEnCrecimiento(HOY.plusDays(10));
            oferta.setDescripcion("Tomate chonto de primera");
            when(ofertaRepository.findByIdAndActivaTrue(oferta.getId())).thenReturn(Optional.of(oferta));

            OfertaDetalleResponse detalle = servicioConReloj(AHORA).obtenerDetalle(oferta.getId());

            assertEquals(oferta.getId(), detalle.id());
            assertEquals("Tomate chonto de primera", detalle.descripcion());
            assertEquals(UnidadMedida.ARROBA, detalle.unidadMedida());
            assertEquals("Finca El Roble", detalle.finca().nombre());
            assertEquals("La Honda", detalle.finca().vereda());
            assertEquals(Municipio.SONSON, detalle.finca().municipio());
            assertEquals(productorId, detalle.productor().id());
            assertEquals("María Gómez", detalle.productor().nombreCompleto());
            assertFalse(detalle.productor().toString().contains("maria@finca.com"));
            assertEquals(10, detalle.diasRestantesCosecha());
        }

        @Test
        @DisplayName("Debe lanzar ResourceNotFoundException si la oferta no existe o está inactiva")
        void testObtenerDetalleNoDisponible() {
            UUID id = UUID.randomUUID();
            when(ofertaRepository.findByIdAndActivaTrue(eq(id))).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> servicioConReloj(AHORA).obtenerDetalle(id));

            assertEquals(OfertaService.MENSAJE_OFERTA_NO_DISPONIBLE, ex.getMessage());
        }

        @ParameterizedTest(name = "cosecha en {0} días -> {1}")
        @CsvSource({"10, 10", "1, 1", "0, 0", "-3, 0"})
        @DisplayName("Debe calcular los días restantes de un cultivo en crecimiento sin valores negativos")
        void testDiasRestantesEnCrecimiento(long desplazamiento, int esperado) {
            Oferta oferta = ofertaEnCrecimiento(HOY.plusDays(desplazamiento));
            when(ofertaRepository.findByIdAndActivaTrue(oferta.getId())).thenReturn(Optional.of(oferta));

            OfertaDetalleResponse detalle = servicioConReloj(AHORA).obtenerDetalle(oferta.getId());

            assertEquals(esperado, detalle.diasRestantesCosecha());
        }

        @Test
        @DisplayName("Debe devolver días restantes null si el producto está disponible")
        void testDiasRestantesDisponible() {
            Oferta oferta = ofertaGuardada(Producto.CAFE_PERGAMINO);
            when(ofertaRepository.findByIdAndActivaTrue(oferta.getId())).thenReturn(Optional.of(oferta));

            assertNull(servicioConReloj(AHORA).obtenerDetalle(oferta.getId()).diasRestantesCosecha());
        }

        @Test
        @DisplayName("Debe devolver días restantes null si el cultivo en crecimiento no tiene fecha")
        void testDiasRestantesSinFecha() {
            Oferta oferta = ofertaEnCrecimiento(null);
            when(ofertaRepository.findByIdAndActivaTrue(oferta.getId())).thenReturn(Optional.of(oferta));

            assertNull(servicioConReloj(AHORA).obtenerDetalle(oferta.getId()).diasRestantesCosecha());
        }

        @Test
        @DisplayName("Debe usar la fecha de Colombia cuando en UTC ya es el día siguiente")
        void testDiasRestantesUsaFechaDeColombia() {
            // 23:30 del 22 de octubre en Bogotá equivale a 04:30 UTC del 23
            Instant nocheEnBogota = Instant.parse("2026-10-23T04:30:00Z");
            Oferta oferta = ofertaEnCrecimiento(LocalDate.of(2026, 10, 23));
            when(ofertaRepository.findByIdAndActivaTrue(oferta.getId())).thenReturn(Optional.of(oferta));

            OfertaDetalleResponse detalle = servicioConReloj(nocheEnBogota).obtenerDetalle(oferta.getId());

            assertEquals(1, detalle.diasRestantesCosecha());
        }
    }
}
