package com.campored.backend.repository;

import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.UnidadMedida;
import com.campored.backend.entity.Usuario;
import com.campored.backend.util.OfertaMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// ============================================================
// FEATURE: US-07 — Catálogo Público de Ofertas (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: Filtros combinables del catálogo sobre H2
// ============================================================
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("OfertaSpecifications - Filtros del catálogo público")
class OfertaSpecificationsTest {

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    private Oferta tomateSonsonDespacho;
    private Oferta cafeSonsonRecogida;
    private Oferta tomateSanCarlosAmbas;
    private Oferta inactivaSanCarlos;

    @BeforeEach
    void setUp() {
        Usuario productorSonson = usuarioRepository.save(nuevoProductor("maria@finca.com", Municipio.SONSON));
        Usuario productorSanCarlos = usuarioRepository.save(nuevoProductor("andres@finca.com", Municipio.SAN_CARLOS));

        tomateSonsonDespacho = guardar(productorSonson, Producto.TOMATE_CHONTO, EstadoCultivo.EN_CRECIMIENTO,
                ModalidadLogistica.DESPACHO_PRODUCTOR, true);
        cafeSonsonRecogida = guardar(productorSonson, Producto.CAFE_PERGAMINO, EstadoCultivo.DISPONIBLE,
                ModalidadLogistica.RECOGIDA_FINCA, true);
        tomateSanCarlosAmbas = guardar(productorSanCarlos, Producto.TOMATE_CHONTO, EstadoCultivo.DISPONIBLE,
                ModalidadLogistica.AMBAS, true);
        inactivaSanCarlos = guardar(productorSanCarlos, Producto.TOMATE_CHONTO, EstadoCultivo.DISPONIBLE,
                ModalidadLogistica.AMBAS, false);
        entityManager.flush();
        entityManager.clear();
    }

    private Usuario nuevoProductor(String correo, Municipio municipio) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Productor " + municipio.getNombre());
        usuario.setCorreo(correo);
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.PRODUCTOR);
        Finca finca = new Finca();
        finca.setNombreFinca("Finca de " + municipio.getNombre());
        finca.setMunicipio(municipio);
        finca.setVereda("La Honda");
        usuario.asignarFinca(finca);
        return usuario;
    }

    private Oferta guardar(Usuario productor, Producto producto, EstadoCultivo estado,
                           ModalidadLogistica modalidad, boolean activa) {
        Oferta oferta = new Oferta();
        oferta.setProductor(productor);
        oferta.setFinca(productor.getFinca());
        oferta.setProducto(producto);
        oferta.setCantidadDisponible(new BigDecimal("40"));
        oferta.setUnidadMedida(UnidadMedida.ARROBA);
        oferta.setPrecioUnitario(new BigDecimal("52000"));
        oferta.setEstadoCultivo(estado);
        oferta.setFechaEstimadaCosecha(estado == EstadoCultivo.EN_CRECIMIENTO ? LocalDate.now().plusDays(10) : null);
        oferta.setModalidadLogistica(modalidad);
        oferta.setActiva(activa);
        return ofertaRepository.save(oferta);
    }

    private Set<UUID> buscar(Specification<Oferta> filtro) {
        return ofertaRepository.findAll(Specification.where(OfertaSpecifications.activas()).and(filtro)).stream()
                .map(Oferta::getId)
                .collect(Collectors.toSet());
    }

    private Set<UUID> ids(Oferta... ofertas) {
        return Stream.of(ofertas).map(Oferta::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Debe excluir las ofertas inactivas")
    void testActivasExcluyeInactivas() {
        Set<UUID> resultado = buscar(null);

        assertEquals(ids(tomateSonsonDespacho, cafeSonsonRecogida, tomateSanCarlosAmbas), resultado);
        assertTrue(ofertaRepository.findById(inactivaSanCarlos.getId()).isPresent());
    }

    @Test
    @DisplayName("Debe filtrar por el municipio de la finca")
    void testConMunicipio() {
        assertEquals(ids(tomateSonsonDespacho, cafeSonsonRecogida),
                buscar(OfertaSpecifications.conMunicipio(Municipio.SONSON)));
        assertEquals(ids(tomateSanCarlosAmbas), buscar(OfertaSpecifications.conMunicipio(Municipio.SAN_CARLOS)));
        assertEquals(Set.of(), buscar(OfertaSpecifications.conMunicipio(Municipio.RIONEGRO)));
    }

    @Test
    @DisplayName("Debe filtrar por estado del cultivo")
    void testConEstadoCultivo() {
        assertEquals(ids(cafeSonsonRecogida, tomateSanCarlosAmbas),
                buscar(OfertaSpecifications.conEstadoCultivo(EstadoCultivo.DISPONIBLE)));
        assertEquals(ids(tomateSonsonDespacho),
                buscar(OfertaSpecifications.conEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO)));
    }

    @Test
    @DisplayName("Debe filtrar por producto")
    void testConProducto() {
        assertEquals(ids(tomateSonsonDespacho, tomateSanCarlosAmbas),
                buscar(OfertaSpecifications.conProducto(Producto.TOMATE_CHONTO)));
        assertEquals(Set.of(), buscar(OfertaSpecifications.conProducto(Producto.AGUACATE)));
    }

    static Stream<Arguments> modalidades() {
        return Stream.of(
                Arguments.of(ModalidadLogistica.DESPACHO_PRODUCTOR, "despacho + ambas"),
                Arguments.of(ModalidadLogistica.RECOGIDA_FINCA, "recogida + ambas"),
                Arguments.of(ModalidadLogistica.AMBAS, "solo ambas"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("modalidades")
    @DisplayName("Debe incluir las ofertas AMBAS al filtrar por una modalidad concreta")
    void testConModalidadLogistica(ModalidadLogistica modalidad, String descripcion) {
        Set<UUID> esperado = switch (modalidad) {
            case DESPACHO_PRODUCTOR -> ids(tomateSonsonDespacho, tomateSanCarlosAmbas);
            case RECOGIDA_FINCA -> ids(cafeSonsonRecogida, tomateSanCarlosAmbas);
            case AMBAS -> ids(tomateSanCarlosAmbas);
        };

        assertEquals(esperado, buscar(OfertaSpecifications.conModalidadLogistica(modalidad)), descripcion);
    }

    @Test
    @DisplayName("Debe combinar filtros con AND")
    void testFiltrosCombinados() {
        Specification<Oferta> sanCarlosTomateDespacho = OfertaSpecifications.conMunicipio(Municipio.SAN_CARLOS)
                .and(OfertaSpecifications.conProducto(Producto.TOMATE_CHONTO))
                .and(OfertaSpecifications.conModalidadLogistica(ModalidadLogistica.DESPACHO_PRODUCTOR));
        Specification<Oferta> sonsonDisponibleTomate = OfertaSpecifications.conMunicipio(Municipio.SONSON)
                .and(OfertaSpecifications.conEstadoCultivo(EstadoCultivo.DISPONIBLE))
                .and(OfertaSpecifications.conProducto(Producto.TOMATE_CHONTO));

        assertEquals(ids(tomateSanCarlosAmbas), buscar(sanCarlosTomateDespacho));
        assertEquals(Set.of(), buscar(sonsonDisponibleTomate));
    }

    @Test
    @DisplayName("Debe devolver null en cada filtro sin valor para no restringir la consulta")
    void testFiltrosNulos() {
        assertNull(OfertaSpecifications.conMunicipio(null));
        assertNull(OfertaSpecifications.conEstadoCultivo(null));
        assertNull(OfertaSpecifications.conProducto(null));
        assertNull(OfertaSpecifications.conModalidadLogistica(null));
    }

    @Test
    @DisplayName("Debe cargar la página con finca y productor en una sola consulta")
    void testPaginaSinConsultasPorOferta() {
        Statistics estadisticas = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        Page<Oferta> pagina = ofertaRepository.findAll(OfertaSpecifications.activas(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "fechaCreacion")));
        pagina.forEach(oferta -> {
            OfertaMapper.toDetalleResponse(oferta, LocalDate.now());
            oferta.getFinca().getProductor().getNombre();
        });

        assertEquals(3, pagina.getNumberOfElements());
        assertEquals(1, estadisticas.getPrepareStatementCount());
    }

    @Test
    @DisplayName("Debe encontrar por id solo si la oferta está activa")
    void testFindByIdAndActivaTrue() {
        Optional<Oferta> activa = ofertaRepository.findByIdAndActivaTrue(tomateSonsonDespacho.getId());

        assertTrue(activa.isPresent());
        assertEquals(Municipio.SONSON, activa.get().getFinca().getMunicipio());
        assertTrue(ofertaRepository.findByIdAndActivaTrue(inactivaSanCarlos.getId()).isEmpty());
        assertTrue(ofertaRepository.findByIdAndActivaTrue(UUID.randomUUID()).isEmpty());
    }
}
