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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                        .value(CrearOfertaRequest.MENSAJE_FECHA_COSECHA_REQUERIDA));

        assertEquals(0, ofertaRepository.count());
    }

    @Test
    @DisplayName("Debe devolver 400 con el detalle de cada campo obligatorio ausente")
    void testCrearOfertaCamposObligatorios() throws Exception {
        mockMvc.perform(postJson(new CrearOfertaRequest()).with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.producto").value("El producto es obligatorio"))
                .andExpect(jsonPath("$.errores.cantidadDisponible").value("La cantidad disponible es obligatoria"))
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
}
