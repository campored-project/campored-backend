package com.campored.backend.controller;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.dto.validation.OfertaValidator;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Negocio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.TipoNegocio;
import com.campored.backend.entity.UnidadMedida;
import com.campored.backend.entity.Usuario;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.service.JwtService;
import com.campored.backend.service.OfertaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("OfertaController - /api/ofertas")
class OfertaControllerIntegrationTest {

    private static final String URL_OFERTAS = "/api/ofertas";
    private static final String URL_MIS_OFERTAS = "/api/ofertas/mis-ofertas";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Value("${jwt.secret}")
    private String secretoJwt;

    private Usuario productor;
    private Usuario comprador;

    @BeforeEach
    void setUp() {
        productor = usuarioRepository.save(nuevoProductor("maria@finca.com", "Finca El Roble"));
        comprador = usuarioRepository.save(nuevoComprador());
    }

    private Usuario nuevoProductor(String correo, String nombreFinca) {
        Usuario usuario = new Usuario();
        usuario.setNombre("María Gómez");
        usuario.setCorreo(correo);
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.PRODUCTOR);
        Finca finca = new Finca();
        finca.setNombreFinca(nombreFinca);
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("La Honda");
        usuario.asignarFinca(finca);
        return usuario;
    }

    private Usuario nuevoComprador() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Laura Restrepo");
        usuario.setCorreo("compras@elfogon.com");
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.COMPRADOR);
        Negocio negocio = new Negocio();
        negocio.setNombreNegocio("Restaurante El Fogón");
        negocio.setTipoNegocio(TipoNegocio.RESTAURANTE);
        negocio.setDireccion("Calle 49 # 50-21");
        negocio.setMunicipio(Municipio.RIONEGRO);
        usuario.asignarNegocio(negocio);
        return usuario;
    }

    private CrearOfertaRequest.CrearOfertaRequestBuilder requestValido() {
        return CrearOfertaRequest.builder()
                .producto(Producto.TOMATE_CHONTO)
                .descripcion("Tomate chonto de primera")
                .cantidadDisponible(new BigDecimal("250.00"))
                .unidadMedida(UnidadMedida.KILOGRAMO)
                .precioUnitario(new BigDecimal("3500"))
                .estadoCultivo(EstadoCultivo.EN_CRECIMIENTO)
                .fechaEstimadaCosecha(LocalDate.now(OfertaValidator.ZONA_COLOMBIA).plusDays(30))
                .modalidadLogistica(ModalidadLogistica.AMBAS);
    }

    private MockHttpServletRequestBuilder postJson(Object cuerpo) throws Exception {
        return post(URL_OFERTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo));
    }

    private RequestPostProcessor autenticadoComo(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        return peticion -> {
            peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return peticion;
        };
    }

    @Test
    @DisplayName("Debe crear la oferta con la finca del productor y devolver 201 con Location")
    void testCrearOfertaExitoso() throws Exception {
        MvcResult resultado = mockMvc.perform(postJson(requestValido().build()).with(autenticadoComo(productor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.producto").value("TOMATE_CHONTO"))
                .andExpect(jsonPath("$.cantidadDisponible").value(250.00))
                .andExpect(jsonPath("$.unidadMedida").value("KILOGRAMO"))
                .andExpect(jsonPath("$.precioUnitario").value(3500))
                .andExpect(jsonPath("$.estadoCultivo").value("EN_CRECIMIENTO"))
                .andExpect(jsonPath("$.modalidadLogistica").value("AMBAS"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.fincaId").value(productor.getFinca().getId().toString()))
                .andExpect(jsonPath("$.fincaNombre").value("Finca El Roble"))
                .andExpect(jsonPath("$.municipio").value("SONSON"))
                .andExpect(jsonPath("$.productor").doesNotExist())
                .andReturn();

        String id = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asText();
        assertTrue(resultado.getResponse().getHeader(HttpHeaders.LOCATION).endsWith(URL_OFERTAS + "/" + id));

        Oferta guardada = ofertaRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(productor.getId(), guardada.getProductor().getId());
        assertEquals(productor.getFinca().getId(), guardada.getFinca().getId());
    }

    @Test
    @DisplayName("Debe guardar productoOtro como null si el producto es del catálogo")
    void testCrearOfertaDescartaProductoOtro() throws Exception {
        CrearOfertaRequest request = requestValido()
                .productoOtro("No aplica")
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .fechaEstimadaCosecha(null)
                .modalidadLogistica(ModalidadLogistica.RECOGIDA_FINCA)
                .build();

        mockMvc.perform(postJson(request).with(autenticadoComo(productor)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString(URL_OFERTAS)))
                .andExpect(jsonPath("$.productoOtro").isEmpty())
                .andExpect(jsonPath("$.fechaEstimadaCosecha").isEmpty());

        List<Oferta> ofertas = ofertaRepository.findAll();
        assertEquals(1, ofertas.size());
        assertNull(ofertas.get(0).getProductoOtro());
    }

    @Test
    @DisplayName("Debe devolver 401 al crear una oferta sin token")
    void testCrearOfertaSinToken() throws Exception {
        mockMvc.perform(postJson(requestValido().build()))
                .andExpect(status().isUnauthorized());

        assertEquals(0, ofertaRepository.count());
    }

    @Test
    @DisplayName("Debe devolver 403 si un comprador intenta crear una oferta")
    void testCrearOfertaConTokenDeComprador() throws Exception {
        mockMvc.perform(postJson(requestValido().build()).with(autenticadoComo(comprador)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(0, ofertaRepository.count());
    }

    @Test
    @DisplayName("Debe devolver 400 si el cultivo está en crecimiento sin fecha de cosecha")
    void testCrearOfertaEnCrecimientoSinFecha() throws Exception {
        CrearOfertaRequest request = requestValido().fechaEstimadaCosecha(null).build();

        mockMvc.perform(postJson(request).with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La solicitud contiene campos inválidos"))
                .andExpect(jsonPath("$.errores.fechaEstimadaCosecha")
                        .value("La fecha estimada de cosecha es obligatoria para productos en cultivo."));

        assertEquals(0, ofertaRepository.count());
    }

    @Test
    @DisplayName("Debe devolver 400 con el mensaje BDD si el producto Otro llega solo con espacios")
    void testCrearOfertaProductoOtroEnBlanco() throws Exception {
        CrearOfertaRequest request = requestValido().producto(Producto.OTRO).productoOtro("   ").build();

        mockMvc.perform(postJson(request).with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.productoOtro").value("Debes especificar el nombre del producto."));

        assertEquals(0, ofertaRepository.count());
    }

    @Test
    @DisplayName("Debe aceptar un producto disponible con fecha de cosecha y guardarla como null")
    void testCrearOfertaDisponibleConFecha() throws Exception {
        CrearOfertaRequest request = requestValido().estadoCultivo(EstadoCultivo.DISPONIBLE).build();

        mockMvc.perform(postJson(request).with(autenticadoComo(productor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoCultivo").value("DISPONIBLE"))
                .andExpect(jsonPath("$.fechaEstimadaCosecha").isEmpty());

        assertNull(ofertaRepository.findAll().get(0).getFechaEstimadaCosecha());
    }

    @Test
    @DisplayName("Debe devolver 400 con el detalle de cada campo obligatorio ausente")
    void testCrearOfertaCamposObligatorios() throws Exception {
        mockMvc.perform(postJson(new CrearOfertaRequest()).with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.producto").value("El producto es obligatorio"))
                .andExpect(jsonPath("$.errores.cantidadDisponible").value("El volumen disponible es obligatorio."))
                .andExpect(jsonPath("$.errores.unidadMedida").value("La unidad de medida es obligatoria"))
                .andExpect(jsonPath("$.errores.precioUnitario").value("El precio unitario es obligatorio"))
                .andExpect(jsonPath("$.errores.estadoCultivo").value("El estado del cultivo es obligatorio"))
                .andExpect(jsonPath("$.errores.modalidadLogistica").value("La modalidad logística es obligatoria"));
    }

    @Test
    @DisplayName("Debe devolver 400 si la unidad de medida no existe en el catálogo")
    void testCrearOfertaUnidadInvalida() throws Exception {
        String cuerpo = objectMapper.writeValueAsString(requestValido().build())
                .replace("\"KILOGRAMO\"", "\"LIBRA\"");

        mockMvc.perform(post(URL_OFERTAS).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Debe devolver 422 si el productor no tiene finca registrada")
    void testCrearOfertaProductorSinFinca() throws Exception {
        Usuario sinFinca = new Usuario();
        sinFinca.setNombre("Pedro Ruiz");
        sinFinca.setCorreo("pedro@finca.com");
        sinFinca.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        sinFinca.setRol(Rol.PRODUCTOR);
        sinFinca = usuarioRepository.save(sinFinca);

        mockMvc.perform(postJson(requestValido().build()).with(autenticadoComo(sinFinca)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.mensaje").value(OfertaService.MENSAJE_PRODUCTOR_SIN_FINCA))
                .andExpect(jsonPath("$.ruta", endsWith(URL_OFERTAS)));

        assertEquals(0, ofertaRepository.count());
    }

    private Oferta nuevaOferta(Usuario dueno, Producto producto) {
        Oferta oferta = new Oferta();
        oferta.setProductor(dueno);
        oferta.setFinca(dueno.getFinca());
        oferta.setProducto(producto);
        oferta.setCantidadDisponible(new BigDecimal("40"));
        oferta.setUnidadMedida(UnidadMedida.ARROBA);
        oferta.setPrecioUnitario(new BigDecimal("52000"));
        oferta.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        oferta.setModalidadLogistica(ModalidadLogistica.DESPACHO_PRODUCTOR);
        return ofertaRepository.save(oferta);
    }

    @Test
    @DisplayName("Debe listar solo las ofertas del productor autenticado y devolver 200")
    void testListarMisOfertas() throws Exception {
        Usuario otroProductor = usuarioRepository.save(nuevoProductor("andres@finca.com", "Finca La Palma"));
        Oferta propia = nuevaOferta(productor, Producto.CAFE_PERGAMINO);
        nuevaOferta(otroProductor, Producto.AGUACATE);

        mockMvc.perform(get(URL_MIS_OFERTAS).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(propia.getId().toString()))
                .andExpect(jsonPath("$[0].producto").value("CAFE_PERGAMINO"))
                .andExpect(jsonPath("$[0].unidadMedida").value("ARROBA"))
                .andExpect(jsonPath("$[0].precioUnitario").value(52000))
                .andExpect(jsonPath("$[0].fincaNombre").value("Finca El Roble"))
                .andExpect(jsonPath("$[0].municipio").value("SONSON"))
                .andExpect(jsonPath("$[0].productor").doesNotExist());
    }

    @Test
    @DisplayName("Debe incluir las ofertas activas e inactivas del productor, de la más reciente a la más antigua")
    void testListarMisOfertasIncluyeInactivas() throws Exception {
        Oferta antigua = publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);
        Oferta inactiva = desactivar(publicar(productor, Producto.CILANTRO, ModalidadLogistica.AMBAS));
        Oferta reciente = enCrecimiento(publicar(productor, Producto.MORA_CASTILLA, ModalidadLogistica.AMBAS), 10);
        publicadaHaceHoras(antigua, 48);
        publicadaHaceHoras(inactiva, 24);
        publicadaHaceHoras(reciente, 1);
        entityManager.clear();

        mockMvc.perform(get(URL_MIS_OFERTAS).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(reciente.getId().toString(),
                        inactiva.getId().toString(), antigua.getId().toString())))
                .andExpect(jsonPath("$[1].activa").value(false))
                .andExpect(jsonPath("$[0].activa").value(true))
                .andExpect(jsonPath("$[0].estadoCultivo").value("EN_CRECIMIENTO"))
                .andExpect(jsonPath("$[0].fechaEstimadaCosecha").isNotEmpty())
                .andExpect(jsonPath("$[0].cantidadDisponible").value(40))
                .andExpect(jsonPath("$[0].unidadMedida").value("ARROBA"))
                .andExpect(jsonPath("$[0].precioUnitario").value(52000))
                .andExpect(jsonPath("$[0].productoOtro").isEmpty());
    }

    @Test
    @DisplayName("Debe devolver 200 con una lista vacía si el productor no tiene ofertas")
    void testListarMisOfertasVacia() throws Exception {
        mockMvc.perform(get(URL_MIS_OFERTAS).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Debe devolver 401 al listar ofertas sin token")
    void testListarMisOfertasSinToken() throws Exception {
        mockMvc.perform(get(URL_MIS_OFERTAS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe devolver 403 si un comprador intenta listar ofertas de productor")
    void testListarMisOfertasConTokenDeComprador() throws Exception {
        mockMvc.perform(get(URL_MIS_OFERTAS).with(autenticadoComo(comprador)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // ============================================================
    // FEATURE: US-11 / US-12 / US-13 — Catálogo y Detalle Públicos de Ofertas (Sprint 2)
    // Autor: Cristian Diez
    // Fecha: 2026-10-22
    // Descripción: Acceso anónimo, filtros, orden, privacidad del productor y regresión de seguridad
    // ============================================================
    private static final String MENSAJE_OFERTA_NO_DISPONIBLE = "La oferta no existe o no está disponible";

    private Usuario productorSanCarlos() {
        Usuario usuario = nuevoProductor("andres@finca.com", "Finca La Palma");
        usuario.setNombre("Andrés Zuluaga");
        usuario.getFinca().setMunicipio(Municipio.SAN_CARLOS);
        usuario.getFinca().setVereda("El Jordán");
        return usuarioRepository.save(usuario);
    }

    private Oferta publicar(Usuario dueno, Producto producto, ModalidadLogistica modalidad) {
        Oferta oferta = new Oferta();
        oferta.setProductor(dueno);
        oferta.setFinca(dueno.getFinca());
        oferta.setProducto(producto);
        oferta.setDescripcion("Cosecha de la semana");
        oferta.setCantidadDisponible(new BigDecimal("40"));
        oferta.setUnidadMedida(UnidadMedida.ARROBA);
        oferta.setPrecioUnitario(new BigDecimal("52000"));
        oferta.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        oferta.setModalidadLogistica(modalidad);
        return ofertaRepository.saveAndFlush(oferta);
    }

    private Oferta enCrecimiento(Oferta oferta, int diasParaCosecha) {
        oferta.setEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO);
        oferta.setFechaEstimadaCosecha(LocalDate.now(OfertaValidator.ZONA_COLOMBIA).plusDays(diasParaCosecha));
        return ofertaRepository.saveAndFlush(oferta);
    }

    private Oferta desactivar(Oferta oferta) {
        oferta.setActiva(false);
        return ofertaRepository.saveAndFlush(oferta);
    }

    // fecha_creacion no es actualizable desde la entidad; se fija por SQL para controlar el orden
    private void publicadaHaceHoras(Oferta oferta, int horas) {
        jdbcTemplate.update("UPDATE ofertas SET fecha_creacion = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(horas)), oferta.getId());
    }

    private String urlDetalle(Object id) {
        return URL_OFERTAS + "/" + id;
    }

    @Test
    @DisplayName("Debe devolver 200 con el catálogo paginado sin token")
    void testCatalogoSinToken() throws Exception {
        Oferta oferta = publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);

        mockMvc.perform(get(URL_OFERTAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(oferta.getId().toString()))
                .andExpect(jsonPath("$.content[0].producto").value("CAFE_PERGAMINO"))
                .andExpect(jsonPath("$.content[0].cantidadDisponible").value(40))
                .andExpect(jsonPath("$.content[0].precioUnitario").value(52000))
                .andExpect(jsonPath("$.content[0].unidadMedida").value("ARROBA"))
                .andExpect(jsonPath("$.content[0].diasRestantesCosecha").isEmpty())
                .andExpect(jsonPath("$.content[0].fechaCreacion").isNotEmpty())
                .andExpect(jsonPath("$.content[0].finca.nombre").value("Finca El Roble"))
                .andExpect(jsonPath("$.content[0].finca.municipio").value("SONSON"))
                .andExpect(jsonPath("$.content[0].productor").doesNotExist())
                .andExpect(jsonPath("$.content[0].descripcion").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @DisplayName("Debe devolver 200 en el catálogo con token de comprador y de productor")
    void testCatalogoConTokenDeCompradorYProductor() throws Exception {
        publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);

        mockMvc.perform(get(URL_OFERTAS).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(get(URL_OFERTAS).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("Debe devolver 200 en el catálogo aunque el token esté expirado")
    void testCatalogoConTokenExpirado() throws Exception {
        publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);
        JwtService emisorVencido = new JwtService(secretoJwt, -Duration.ofMinutes(1).toMillis());

        mockMvc.perform(get(URL_OFERTAS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisorVencido.generarToken(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("Debe filtrar por municipio sin distinguir mayúsculas")
    void testCatalogoFiltroMunicipio() throws Exception {
        Usuario sanCarlos = productorSanCarlos();
        Oferta enSonson = publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);
        Oferta enSanCarlos = publicar(sanCarlos, Producto.AGUACATE, ModalidadLogistica.RECOGIDA_FINCA);

        mockMvc.perform(get(URL_OFERTAS).param("municipio", "sonson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(enSonson.getId().toString())));
        mockMvc.perform(get(URL_OFERTAS).param("municipio", "San Carlos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(enSanCarlos.getId().toString())))
                .andExpect(jsonPath("$.content[0].finca.municipio").value("SAN_CARLOS"))
                .andExpect(jsonPath("$.content[0].finca.vereda").value("El Jordán"));
    }

    @Test
    @DisplayName("Debe combinar filtros e incluir las ofertas AMBAS al filtrar por modalidad")
    void testCatalogoFiltrosCombinados() throws Exception {
        Usuario sanCarlos = productorSanCarlos();
        Oferta tomateDespacho = enCrecimiento(
                publicar(sanCarlos, Producto.TOMATE_CHONTO, ModalidadLogistica.DESPACHO_PRODUCTOR), 15);
        Oferta tomateAmbas = enCrecimiento(publicar(sanCarlos, Producto.TOMATE_CHONTO, ModalidadLogistica.AMBAS), 20);
        publicar(sanCarlos, Producto.TOMATE_CHONTO, ModalidadLogistica.RECOGIDA_FINCA);
        publicar(sanCarlos, Producto.AGUACATE, ModalidadLogistica.DESPACHO_PRODUCTOR);
        enCrecimiento(publicar(productor, Producto.TOMATE_CHONTO, ModalidadLogistica.DESPACHO_PRODUCTOR), 15);

        mockMvc.perform(get(URL_OFERTAS)
                        .param("municipio", "SAN_CARLOS")
                        .param("producto", "TOMATE_CHONTO")
                        .param("estadoCultivo", "EN_CRECIMIENTO")
                        .param("modalidadLogistica", "DESPACHO_PRODUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(
                        tomateDespacho.getId().toString(), tomateAmbas.getId().toString())));

        mockMvc.perform(get(URL_OFERTAS).param("municipio", "SAN_CARLOS").param("modalidadLogistica", "AMBAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(tomateAmbas.getId().toString())));
    }

    @Test
    @DisplayName("Debe excluir las ofertas inactivas y ordenar de la más reciente a la más antigua")
    void testCatalogoOrdenYActivas() throws Exception {
        Oferta antigua = publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);
        Oferta reciente = publicar(productor, Producto.MORA_CASTILLA, ModalidadLogistica.AMBAS);
        Oferta intermedia = publicar(productor, Producto.CILANTRO, ModalidadLogistica.AMBAS);
        Oferta inactiva = desactivar(publicar(productor, Producto.PLATANO, ModalidadLogistica.AMBAS));
        publicadaHaceHoras(antigua, 48);
        publicadaHaceHoras(reciente, 1);
        publicadaHaceHoras(intermedia, 24);
        publicadaHaceHoras(inactiva, 0);
        entityManager.clear();

        mockMvc.perform(get(URL_OFERTAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].id", contains(
                        reciente.getId().toString(), intermedia.getId().toString(), antigua.getId().toString())));

        mockMvc.perform(get(URL_OFERTAS).param("sort", "fechaCreacion,asc").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(
                        antigua.getId().toString(), intermedia.getId().toString())));
    }

    @Test
    @DisplayName("Debe limitar el tamaño de página a 50")
    void testCatalogoLimitaTamano() throws Exception {
        mockMvc.perform(get(URL_OFERTAS).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(OfertaService.TAMANO_MAXIMO_PAGINA));
    }

    @Test
    @DisplayName("Debe devolver 400 si un filtro tiene un valor fuera del catálogo")
    void testCatalogoFiltrosInvalidos() throws Exception {
        mockMvc.perform(get(URL_OFERTAS).param("estadoCultivo", "INVALIDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje", containsString("estadoCultivo")))
                .andExpect(jsonPath("$.mensaje", containsString("EN_CRECIMIENTO, DISPONIBLE")))
                .andExpect(jsonPath("$.ruta").value(URL_OFERTAS));
        mockMvc.perform(get(URL_OFERTAS).param("municipio", "BOGOTA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El municipio 'BOGOTA' no es válido"));
        mockMvc.perform(get(URL_OFERTAS).param("modalidadLogistica", "DOMICILIO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Debe devolver 400 si se ordena por un campo no permitido")
    void testCatalogoOrdenNoPermitido() throws Exception {
        mockMvc.perform(get(URL_OFERTAS).param("sort", "productor.correo,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("productor.correo")));
    }

    @Test
    @DisplayName("Debe devolver 200 con el detalle de la oferta sin token")
    void testDetalleSinToken() throws Exception {
        Oferta oferta = enCrecimiento(publicar(productor, Producto.TOMATE_CHONTO, ModalidadLogistica.AMBAS), 10);

        mockMvc.perform(get(urlDetalle(oferta.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(oferta.getId().toString()))
                .andExpect(jsonPath("$.producto").value("TOMATE_CHONTO"))
                .andExpect(jsonPath("$.descripcion").value("Cosecha de la semana"))
                .andExpect(jsonPath("$.estadoCultivo").value("EN_CRECIMIENTO"))
                .andExpect(jsonPath("$.diasRestantesCosecha").value(10))
                .andExpect(jsonPath("$.modalidadLogistica").value("AMBAS"))
                .andExpect(jsonPath("$.finca.id").value(productor.getFinca().getId().toString()))
                .andExpect(jsonPath("$.finca.nombre").value("Finca El Roble"))
                .andExpect(jsonPath("$.finca.vereda").value("La Honda"))
                .andExpect(jsonPath("$.finca.municipio").value("SONSON"))
                .andExpect(jsonPath("$.productor.id").value(productor.getId().toString()))
                .andExpect(jsonPath("$.productor.nombreCompleto").value("María Gómez"));
    }

    private Oferta ofertaDeProductorContactable() {
        productor.setTelefono("+573001234567");
        productor.setWhatsapp("+573109876543");
        productor.setCanalLlamadaHabilitado(true);
        productor.setCanalWhatsappHabilitado(true);
        usuarioRepository.saveAndFlush(productor);
        return publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);
    }

    @Test
    @DisplayName("Debe omitir los datos sensibles y los números del productor para un visitante anónimo")
    void testDetalleSinDatosSensibles() throws Exception {
        Oferta oferta = ofertaDeProductorContactable();

        String cuerpo = mockMvc.perform(get(urlDetalle(oferta.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productor.whatsappDisponible").value(true))
                .andExpect(jsonPath("$.productor.llamadaDisponible").value(true))
                .andExpect(jsonPath("$.productor.telefonoContacto").isEmpty())
                .andExpect(jsonPath("$.productor.whatsappContacto").isEmpty())
                .andExpect(jsonPath("$.productor.email").doesNotExist())
                .andExpect(jsonPath("$.productor.correo").doesNotExist())
                .andExpect(jsonPath("$.productor.password").doesNotExist())
                .andExpect(jsonPath("$.productor.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.productor.documento").doesNotExist())
                .andExpect(jsonPath("$.productor.telefono").doesNotExist())
                .andExpect(jsonPath("$.productor.whatsapp").doesNotExist())
                .andExpect(jsonPath("$.productor.rol").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertFalse(cuerpo.contains("maria@finca.com"), "El detalle expone el correo: " + cuerpo);
        assertFalse(cuerpo.contains("+573001234567"), "El detalle expone el teléfono: " + cuerpo);
        assertFalse(cuerpo.contains("+573109876543"), "El detalle expone el WhatsApp: " + cuerpo);
    }

    @Test
    @DisplayName("Debe entregar los números de contacto a un comprador autenticado")
    void testDetalleContactoParaComprador() throws Exception {
        Oferta oferta = ofertaDeProductorContactable();

        String cuerpo = mockMvc.perform(get(urlDetalle(oferta.getId())).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productor.telefonoContacto").value("+573001234567"))
                .andExpect(jsonPath("$.productor.whatsappContacto").value("+573109876543"))
                .andExpect(jsonPath("$.productor.correo").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertFalse(cuerpo.contains("maria@finca.com"), "El detalle expone el correo: " + cuerpo);
    }

    @Test
    @DisplayName("No debe entregar los números de contacto a un productor autenticado")
    void testDetalleSinContactoParaProductor() throws Exception {
        Oferta oferta = ofertaDeProductorContactable();
        Usuario otroProductor = usuarioRepository.save(nuevoProductor("andres@finca.com", "Finca La Palma"));

        mockMvc.perform(get(urlDetalle(oferta.getId())).with(autenticadoComo(otroProductor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productor.llamadaDisponible").value(true))
                .andExpect(jsonPath("$.productor.telefonoContacto").isEmpty())
                .andExpect(jsonPath("$.productor.whatsappContacto").isEmpty());
        mockMvc.perform(get(urlDetalle(oferta.getId())).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productor.telefonoContacto").isEmpty());
    }

    @Test
    @DisplayName("Debe marcar WhatsApp como no disponible si el canal está habilitado pero sin número")
    void testDetalleWhatsappHabilitadoSinNumero() throws Exception {
        productor.setCanalWhatsappHabilitado(true);
        usuarioRepository.saveAndFlush(productor);
        Oferta oferta = publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);

        mockMvc.perform(get(urlDetalle(oferta.getId())).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productor.whatsappDisponible").value(false))
                .andExpect(jsonPath("$.productor.llamadaDisponible").value(false))
                .andExpect(jsonPath("$.productor.whatsappContacto").isEmpty());
    }

    @Test
    @DisplayName("No debe exponer números de contacto en el catálogo, ni siquiera a compradores")
    void testCatalogoSinDatosDeContacto() throws Exception {
        ofertaDeProductorContactable();

        String cuerpo = mockMvc.perform(get(URL_OFERTAS).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productor").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertFalse(cuerpo.contains("+573001234567") || cuerpo.contains("+573109876543"),
                "El catálogo expone datos de contacto: " + cuerpo);
    }

    @Test
    @DisplayName("Debe devolver 404 si la oferta no existe o está inactiva, con el mismo mensaje")
    void testDetalleNoDisponible() throws Exception {
        Oferta inactiva = desactivar(publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS));

        mockMvc.perform(get(urlDetalle(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_OFERTA_NO_DISPONIBLE));
        mockMvc.perform(get(urlDetalle(inactiva.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_OFERTA_NO_DISPONIBLE));
    }

    @Test
    @DisplayName("Debe devolver 400 si el id no es un UUID y el usuario está autenticado")
    void testDetalleIdInvalido() throws Exception {
        mockMvc.perform(get(urlDetalle("no-es-un-uuid")).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("'id'")));
    }

    @Test
    @DisplayName("Debe mantener protegidas las demás rutas de ofertas sin token")
    void testRegresionSeguridadOfertas() throws Exception {
        Oferta oferta = publicar(productor, Producto.CAFE_PERGAMINO, ModalidadLogistica.AMBAS);

        // Solo un UUID abre el detalle anónimo; cualquier otra subruta exige token
        mockMvc.perform(get(urlDetalle("no-es-un-uuid")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL_MIS_OFERTAS))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(postJson(requestValido().build()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(urlDetalle(oferta.getId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(urlDetalle(oferta.getId())).param("origen", "catalogo"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Debe documentar el catálogo y el detalle como endpoints sin seguridad en OpenAPI")
    void testOpenApiEndpointsPublicos() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/ofertas'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/ofertas/{id}'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/ofertas/mis-ofertas'].get.security").doesNotExist());
    }
}
