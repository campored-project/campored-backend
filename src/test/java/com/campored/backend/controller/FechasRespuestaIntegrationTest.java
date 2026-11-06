// ============================================================
// FEATURE: US-22 — Manejo de Errores y Formato de Respuestas (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-06
// Descripción: Las fechas con hora salen en el JSON con el offset de Colombia (-05:00)
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.dto.CrearPedidoRequest;
import com.campored.backend.dto.LoginRequest;
import com.campored.backend.dto.RegistrarContactoRequest;
import com.campored.backend.dto.validation.OfertaValidator;
import com.campored.backend.entity.CanalContacto;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Jornada;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// surefire corre los tests con user.timezone=UTC, como Render: el offset -05:00 no sale de la zona del equipo
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("US-22 — Fechas del API con el offset de Colombia")
class FechasRespuestaIntegrationTest {

    private static final String OFFSET_COLOMBIA = "-05:00";
    private static final String CONTRASENA = "SecurePass123";

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
    private PasswordEncoder passwordEncoder;

    private Usuario productor;
    private Usuario comprador;
    private Oferta oferta;
    private LocalDate hoy;

    @BeforeEach
    void setUp() {
        hoy = LocalDate.now(OfertaValidator.ZONA_COLOMBIA);
        productor = usuarioRepository.save(nuevoProductor());
        comprador = usuarioRepository.save(nuevoComprador());
        oferta = publicar();
    }

    private Usuario nuevoProductor() {
        Usuario usuario = new Usuario();
        usuario.setNombre("María Gómez");
        usuario.setCorreo("maria@finca.com");
        usuario.setContrasenaHash(passwordEncoder.encode(CONTRASENA));
        usuario.setRol(Rol.PRODUCTOR);
        usuario.setWhatsapp("+573109876543");
        usuario.setTelefono("+573001234567");
        usuario.setCanalWhatsappHabilitado(true);
        Finca finca = new Finca();
        finca.setNombreFinca("Finca El Roble");
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("La Honda");
        usuario.asignarFinca(finca);
        return usuario;
    }

    private Usuario nuevoComprador() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Laura Restrepo");
        usuario.setCorreo("compras@elfogon.com");
        usuario.setContrasenaHash(passwordEncoder.encode(CONTRASENA));
        usuario.setRol(Rol.COMPRADOR);
        Negocio negocio = new Negocio();
        negocio.setNombreNegocio("Restaurante El Fogón");
        negocio.setTipoNegocio(TipoNegocio.RESTAURANTE);
        negocio.setDireccion("Calle 49 # 50-21");
        negocio.setMunicipio(Municipio.RIONEGRO);
        usuario.asignarNegocio(negocio);
        return usuario;
    }

    private Oferta publicar() {
        Oferta nueva = new Oferta();
        nueva.setProductor(productor);
        nueva.setFinca(productor.getFinca());
        nueva.setProducto(Producto.TOMATE_CHONTO);
        nueva.setCantidadDisponible(new BigDecimal("250.00"));
        nueva.setUnidadMedida(UnidadMedida.KILOGRAMO);
        nueva.setPrecioUnitario(new BigDecimal("3500.00"));
        nueva.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        nueva.setModalidadLogistica(ModalidadLogistica.RECOGIDA_FINCA);
        return ofertaRepository.saveAndFlush(nueva);
    }

    private CrearPedidoRequest.CrearPedidoRequestBuilder pedidoValido() {
        return CrearPedidoRequest.builder()
                .ofertaId(oferta.getId())
                .cantidadSolicitada(new BigDecimal("10.00"))
                .direccionEntrega("Calle 49 # 50-21, local 3")
                .municipioEntrega("RIONEGRO")
                .fechaPreferida(hoy)
                .jornada(Jornada.MANANA);
    }

    private MockHttpServletRequestBuilder postJson(String url, Object cuerpo) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo));
    }

    private RequestPostProcessor autenticadoComo(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        return peticion -> {
            peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return peticion;
        };
    }

    // El texto debe llevar el offset de Colombia y representar el instante actual, no la hora UTC con otro offset
    private void assertHoraColombia(MvcResult resultado, String ruta) throws Exception {
        String valor = JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
        assertTrue(valor.endsWith(OFFSET_COLOMBIA), ruta + " sin offset de Colombia: " + valor);
        Duration diferencia = Duration.between(OffsetDateTime.parse(valor), OffsetDateTime.now()).abs();
        assertTrue(diferencia.compareTo(Duration.ofMinutes(5)) < 0, ruta + " no corresponde a la hora actual: " + valor);
    }

    @Test
    @DisplayName("US-22 — POST /api/pedidos devuelve fechaCreacion con offset -05:00")
    void testPedidoConOffsetColombia() throws Exception {
        MvcResult resultado = mockMvc.perform(postJson("/api/pedidos", pedidoValido().build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isCreated())
                .andReturn();

        assertHoraColombia(resultado, "$.fechaCreacion");
    }

    @Test
    @DisplayName("US-22 — POST /api/contactos devuelve fechaContacto con offset -05:00")
    void testContactoConOffsetColombia() throws Exception {
        RegistrarContactoRequest contacto = RegistrarContactoRequest.builder()
                .ofertaId(oferta.getId())
                .canal(CanalContacto.WHATSAPP)
                .build();

        MvcResult resultado = mockMvc.perform(postJson("/api/contactos", contacto).with(autenticadoComo(comprador)))
                .andExpect(status().isCreated())
                .andReturn();

        assertHoraColombia(resultado, "$.fechaContacto");
    }

    @Test
    @DisplayName("US-22 — POST /api/ofertas devuelve fechaCreacion con offset -05:00")
    void testOfertaConOffsetColombia() throws Exception {
        CrearOfertaRequest nueva = CrearOfertaRequest.builder()
                .producto(Producto.AGUACATE)
                .cantidadDisponible(new BigDecimal("80.00"))
                .unidadMedida(UnidadMedida.KILOGRAMO)
                .precioUnitario(new BigDecimal("5200"))
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .modalidadLogistica(ModalidadLogistica.AMBAS)
                .build();

        MvcResult resultado = mockMvc.perform(postJson("/api/ofertas", nueva).with(autenticadoComo(productor)))
                .andExpect(status().isCreated())
                .andReturn();

        assertHoraColombia(resultado, "$.fechaCreacion");
    }

    @Test
    @DisplayName("US-22 — POST /api/auth/login devuelve usuario.fechaRegistro con offset -05:00")
    void testLoginConOffsetColombia() throws Exception {
        MvcResult resultado = mockMvc.perform(postJson("/api/auth/login",
                        new LoginRequest(comprador.getCorreo(), CONTRASENA)))
                .andExpect(status().isOk())
                .andReturn();

        assertHoraColombia(resultado, "$.usuario.fechaRegistro");
    }

    @Test
    @DisplayName("US-22 — un 400 devuelve el timestamp de ErrorResponse con offset -05:00")
    void testErrorConOffsetColombia() throws Exception {
        MvcResult resultado = mockMvc.perform(postJson("/api/pedidos", pedidoValido().direccionEntrega(null).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertHoraColombia(resultado, "$.timestamp");
    }
}
