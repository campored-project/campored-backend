// ============================================================
// FEATURE: US-19 — Historial de Contactos (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: POST /api/contactos y GET /api/contactos/mios con JWT reales: reglas, orden y aislamiento
// ============================================================
package com.campored.backend.controller;

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
import com.campored.backend.entity.Pedido;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.RegistroContacto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.TipoNegocio;
import com.campored.backend.entity.UnidadMedida;
import com.campored.backend.entity.Usuario;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.PedidoRepository;
import com.campored.backend.repository.RegistroContactoRepository;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.service.ContactoService;
import com.campored.backend.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ContactoController - /api/contactos")
class ContactoControllerIntegrationTest {

    private static final String URL_CONTACTOS = "/api/contactos";
    private static final String URL_MIS_CONTACTOS = "/api/contactos/mios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private RegistroContactoRepository registroContactoRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Usuario productor;
    private Usuario comprador;
    private Oferta oferta;

    @BeforeEach
    void setUp() {
        productor = usuarioRepository.save(nuevoProductor("maria@finca.com", "Finca El Roble"));
        comprador = usuarioRepository.save(nuevoComprador("compras@elfogon.com"));
        oferta = publicar(productor, Producto.TOMATE_CHONTO);
    }

    // Los dos canales habilitados y con número; cada prueba apaga el que necesite
    private Usuario nuevoProductor(String correo, String nombreFinca) {
        Usuario usuario = new Usuario();
        usuario.setNombre("María Gómez");
        usuario.setCorreo(correo);
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.PRODUCTOR);
        usuario.setWhatsapp("+573109876543");
        usuario.setTelefono("+573001234567");
        usuario.setCanalWhatsappHabilitado(true);
        usuario.setCanalLlamadaHabilitado(true);
        Finca finca = new Finca();
        finca.setNombreFinca(nombreFinca);
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("La Honda");
        usuario.asignarFinca(finca);
        return usuario;
    }

    private Usuario nuevoComprador(String correo) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Laura Restrepo");
        usuario.setCorreo(correo);
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

    private Oferta publicar(Usuario dueno, Producto producto) {
        Oferta nueva = new Oferta();
        nueva.setProductor(dueno);
        nueva.setFinca(dueno.getFinca());
        nueva.setProducto(producto);
        nueva.setCantidadDisponible(new BigDecimal("250.00"));
        nueva.setUnidadMedida(UnidadMedida.KILOGRAMO);
        nueva.setPrecioUnitario(new BigDecimal("3500.00"));
        nueva.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        nueva.setModalidadLogistica(ModalidadLogistica.AMBAS);
        return ofertaRepository.saveAndFlush(nueva);
    }

    private Pedido pedido(Usuario dueno, Oferta ofertaPedida) {
        Pedido pedido = new Pedido();
        pedido.setOferta(ofertaPedida);
        pedido.setComprador(dueno);
        pedido.setCantidadSolicitada(new BigDecimal("10.00"));
        pedido.setPrecioUnitario(new BigDecimal("3500.00"));
        pedido.setPrecioTotalEstimado(new BigDecimal("35000.00"));
        pedido.setDireccionEntrega("Calle 49 # 50-21");
        pedido.setMunicipioEntrega(Municipio.RIONEGRO);
        pedido.setFechaPreferida(LocalDate.now(OfertaValidator.ZONA_COLOMBIA).plusDays(2));
        pedido.setJornada(Jornada.TARDE);
        pedido.setModalidadEntrega(ModalidadLogistica.RECOGIDA_FINCA);
        return pedidoRepository.saveAndFlush(pedido);
    }

    // fecha_contacto no es actualizable desde la entidad; se fija por SQL para que el orden sea determinista
    private RegistroContacto contactoHaceHoras(Usuario dueno, Oferta ofertaContactada, CanalContacto canal, int horas) {
        RegistroContacto registro = new RegistroContacto();
        registro.setComprador(dueno);
        registro.setOferta(ofertaContactada);
        registro.setCanal(canal);
        RegistroContacto guardado = registroContactoRepository.saveAndFlush(registro);
        jdbcTemplate.update("UPDATE registros_contacto SET fecha_contacto = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(horas)), guardado.getId());
        return guardado;
    }

    private RegistrarContactoRequest request(CanalContacto canal, UUID pedidoId) {
        return RegistrarContactoRequest.builder().ofertaId(oferta.getId()).canal(canal).pedidoId(pedidoId).build();
    }

    private MockHttpServletRequestBuilder postJson(Object cuerpo) throws Exception {
        return post(URL_CONTACTOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo instanceof String texto ? texto : objectMapper.writeValueAsString(cuerpo));
    }

    private RequestPostProcessor autenticadoComo(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        return peticion -> {
            peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return peticion;
        };
    }

    private void apagarLlamada() {
        productor.setCanalLlamadaHabilitado(false);
        usuarioRepository.saveAndFlush(productor);
    }

    // ---------- POST /api/contactos ----------

    @Test
    @DisplayName("US-19 Esc 1 — registra el contacto y responde 201 con los campos completos y sin números")
    void testRegistrarContactoExitoso() throws Exception {
        MvcResult resultado = mockMvc.perform(postJson(request(CanalContacto.WHATSAPP, null))
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fechaContacto").isNotEmpty())
                .andExpect(jsonPath("$.canal").value("WHATSAPP"))
                .andExpect(jsonPath("$.ofertaId").value(oferta.getId().toString()))
                .andExpect(jsonPath("$.pedidoId").isEmpty())
                .andExpect(jsonPath("$.producto").value("Tomate chonto"))
                .andExpect(jsonPath("$.productorNombre").value("María Gómez"))
                .andExpect(jsonPath("$.fincaNombre").value("Finca El Roble"))
                .andExpect(jsonPath("$.whatsappContacto").doesNotExist())
                .andExpect(jsonPath("$.telefonoContacto").doesNotExist())
                .andReturn();

        String id = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asText();
        entityManager.flush();
        entityManager.clear();
        RegistroContacto guardado = registroContactoRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(comprador.getId(), guardado.getComprador().getId());
        assertEquals(oferta.getId(), guardado.getOferta().getId());
        assertEquals(CanalContacto.WHATSAPP, guardado.getCanal());
        assertNull(guardado.getPedido());
    }

    @Test
    @DisplayName("US-19 Regla — una oferta OTRO muestra productoOtro como nombre del producto")
    void testRegistrarContactoProductoOtro() throws Exception {
        oferta.setProducto(Producto.OTRO);
        oferta.setProductoOtro("Lulo");
        ofertaRepository.saveAndFlush(oferta);

        mockMvc.perform(postJson(request(CanalContacto.LLAMADA, null)).with(autenticadoComo(comprador)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.producto").value("Lulo"));
    }

    @Test
    @DisplayName("US-19 Regla — un contacto con una oferta agotada se registra igual")
    void testRegistrarContactoOfertaAgotada() throws Exception {
        oferta.setActiva(false);
        ofertaRepository.saveAndFlush(oferta);

        mockMvc.perform(postJson(request(CanalContacto.WHATSAPP, null)).with(autenticadoComo(comprador)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ofertaId").value(oferta.getId().toString()));

        assertEquals(1, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — sin oferta responde 400 en errores.ofertaId")
    void testRegistrarContactoSinOferta() throws Exception {
        RegistrarContactoRequest sinOferta = RegistrarContactoRequest.builder().canal(CanalContacto.WHATSAPP).build();

        mockMvc.perform(postJson(sinOferta).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.ofertaId").value(RegistrarContactoRequest.MENSAJE_OFERTA_REQUERIDA));

        assertEquals(0, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — sin canal responde 400 en errores.canal")
    void testRegistrarContactoSinCanal() throws Exception {
        mockMvc.perform(postJson(request(null, null)).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.canal").value(RegistrarContactoRequest.MENSAJE_CANAL_REQUERIDO));

        assertEquals(0, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — un canal fuera del catálogo responde 400")
    void testRegistrarContactoCanalInvalido() throws Exception {
        String cuerpo = "{\"ofertaId\":\"" + oferta.getId() + "\",\"canal\":\"SMS\"}";

        mockMvc.perform(postJson(cuerpo).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(0, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — una oferta inexistente responde 404")
    void testRegistrarContactoOfertaInexistente() throws Exception {
        RegistrarContactoRequest inexistente = RegistrarContactoRequest.builder()
                .ofertaId(UUID.randomUUID()).canal(CanalContacto.WHATSAPP).build();

        mockMvc.perform(postJson(inexistente).with(autenticadoComo(comprador)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(ContactoService.MENSAJE_OFERTA_NO_ENCONTRADA));
    }

    @Test
    @DisplayName("US-19 Regla — un canal apagado por el productor responde 422 y no se registra")
    void testRegistrarContactoCanalApagado() throws Exception {
        apagarLlamada();

        mockMvc.perform(postJson(request(CanalContacto.LLAMADA, null)).with(autenticadoComo(comprador)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(ContactoService.MENSAJE_CANAL_NO_HABILITADO));

        assertEquals(0, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — un pedido de otro comprador responde 422")
    void testRegistrarContactoPedidoDeOtroComprador() throws Exception {
        Usuario otroComprador = usuarioRepository.save(nuevoComprador("compras@laparrilla.com"));
        Pedido ajeno = pedido(otroComprador, oferta);

        mockMvc.perform(postJson(request(CanalContacto.WHATSAPP, ajeno.getId())).with(autenticadoComo(comprador)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(ContactoService.MENSAJE_PEDIDO_NO_CORRESPONDE));

        assertEquals(0, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — un pedido propio de otra oferta o inexistente responde 422 con el mismo mensaje")
    void testRegistrarContactoPedidoDeOtraOferta() throws Exception {
        Pedido deOtraOferta = pedido(comprador, publicar(productor, Producto.AGUACATE));

        mockMvc.perform(postJson(request(CanalContacto.WHATSAPP, deOtraOferta.getId())).with(autenticadoComo(comprador)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(ContactoService.MENSAJE_PEDIDO_NO_CORRESPONDE));
        mockMvc.perform(postJson(request(CanalContacto.WHATSAPP, UUID.randomUUID())).with(autenticadoComo(comprador)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(ContactoService.MENSAJE_PEDIDO_NO_CORRESPONDE));

        assertEquals(0, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — un productor no puede registrar contactos (403)")
    void testRegistrarContactoComoProductor() throws Exception {
        mockMvc.perform(postJson(request(CanalContacto.WHATSAPP, null)).with(autenticadoComo(productor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(0, registroContactoRepository.count());
    }

    @Test
    @DisplayName("US-19 Regla — registrar un contacto sin token responde 401")
    void testRegistrarContactoSinToken() throws Exception {
        mockMvc.perform(postJson(request(CanalContacto.WHATSAPP, null)))
                .andExpect(status().isUnauthorized());

        assertEquals(0, registroContactoRepository.count());
    }

    // ---------- GET /api/contactos/mios ----------

    @Test
    @DisplayName("US-19 Esc 2 — lista los contactos del comprador del más reciente al más antiguo")
    void testListarMisContactosOrdenados() throws Exception {
        Oferta aguacate = publicar(productor, Producto.AGUACATE);
        RegistroContacto antiguo = contactoHaceHoras(comprador, oferta, CanalContacto.WHATSAPP, 3);
        RegistroContacto reciente = contactoHaceHoras(comprador, aguacate, CanalContacto.LLAMADA, 1);
        RegistroContacto intermedio = contactoHaceHoras(comprador, oferta, CanalContacto.LLAMADA, 2);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(URL_MIS_CONTACTOS).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains(reciente.getId().toString(),
                        intermedio.getId().toString(), antiguo.getId().toString())))
                .andExpect(jsonPath("$[0].producto").value("Aguacate"))
                .andExpect(jsonPath("$[0].productorNombre").value("María Gómez"))
                .andExpect(jsonPath("$[0].fincaNombre").value("Finca El Roble"))
                .andExpect(jsonPath("$[0].canal").value("LLAMADA"))
                .andExpect(jsonPath("$[0].fechaContacto").isNotEmpty())
                .andExpect(jsonPath("$[2].canal").value("WHATSAPP"));
    }

    @Test
    @DisplayName("US-19 Esc 3 — sin contactos responde 200 con una lista vacía")
    void testListarMisContactosVacio() throws Exception {
        mockMvc.perform(get(URL_MIS_CONTACTOS).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("US-19 Regla — un comprador no ve los contactos de otro comprador")
    void testListarMisContactosAislamiento() throws Exception {
        Usuario otroComprador = usuarioRepository.save(nuevoComprador("compras@laparrilla.com"));
        RegistroContacto propio = contactoHaceHoras(comprador, oferta, CanalContacto.WHATSAPP, 1);
        RegistroContacto ajeno = contactoHaceHoras(otroComprador, oferta, CanalContacto.LLAMADA, 1);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(URL_MIS_CONTACTOS).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(propio.getId().toString())));
        mockMvc.perform(get(URL_MIS_CONTACTOS).with(autenticadoComo(otroComprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(ajeno.getId().toString())));
    }

    @Test
    @DisplayName("US-19 Regla — un productor no puede consultar el historial de contactos (403)")
    void testListarMisContactosComoProductor() throws Exception {
        mockMvc.perform(get(URL_MIS_CONTACTOS).with(autenticadoComo(productor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("US-19 Regla — consultar el historial sin token responde 401")
    void testListarMisContactosSinToken() throws Exception {
        mockMvc.perform(get(URL_MIS_CONTACTOS))
                .andExpect(status().isUnauthorized());
    }
}
