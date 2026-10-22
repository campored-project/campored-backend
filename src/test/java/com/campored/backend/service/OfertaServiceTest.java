package com.campored.backend.service;

import com.campored.backend.dto.CrearOfertaRequest;
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
import com.campored.backend.exception.ReglaNegocioException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}
