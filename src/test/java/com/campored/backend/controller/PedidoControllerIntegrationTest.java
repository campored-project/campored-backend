// ============================================================
// FEATURE: US-18 — Checkout y Pedido (Sprint 3)
// Autor: Cristian Diez
// Fecha: 2026-11-05
// Descripción: POST y GET /api/pedidos con JWT reales: reglas del checkout, permisos y contacto
// ============================================================
package com.campored.backend.controller;

import com.campored.backend.dto.CrearPedidoRequest;
import com.campored.backend.dto.validation.OfertaValidator;
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
import com.campored.backend.exception.GlobalExceptionHandler;
import com.campored.backend.repository.OfertaRepository;
import com.campored.backend.repository.PedidoRepository;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.service.JwtService;
import com.campored.backend.service.PedidoService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("PedidoController - /api/pedidos")
class PedidoControllerIntegrationTest {

    private static final String URL_PEDIDOS = "/api/pedidos";

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
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    private Usuario productor;
    private Usuario comprador;
    private Oferta oferta;
    private LocalDate hoy;

    @BeforeEach
    void setUp() {
        hoy = LocalDate.now(OfertaValidator.ZONA_COLOMBIA);
        productor = usuarioRepository.save(nuevoProductor("maria@finca.com", "Finca El Roble"));
        comprador = usuarioRepository.save(nuevoComprador("compras@elfogon.com", "Restaurante El Fogón"));
        oferta = publicar(productor, ModalidadLogistica.AMBAS);
    }

    // WhatsApp habilitado con número y llamada apagada aunque tenga teléfono: el pedido solo expone el WhatsApp
    private Usuario nuevoProductor(String correo, String nombreFinca) {
        Usuario usuario = new Usuario();
        usuario.setNombre("María Gómez");
        usuario.setCorreo(correo);
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.PRODUCTOR);
        usuario.setWhatsapp("+573109876543");
        usuario.setTelefono("+573001234567");
        usuario.setCanalWhatsappHabilitado(true);
        usuario.setCanalLlamadaHabilitado(false);
        Finca finca = new Finca();
        finca.setNombreFinca(nombreFinca);
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("La Honda");
        usuario.asignarFinca(finca);
        return usuario;
    }

    private Usuario nuevoComprador(String correo, String nombreNegocio) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Laura Restrepo");
        usuario.setCorreo(correo);
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.COMPRADOR);
        usuario.setWhatsapp("+573201112233");
        usuario.setTelefono("+576045551234");
        Negocio negocio = new Negocio();
        negocio.setNombreNegocio(nombreNegocio);
        negocio.setTipoNegocio(TipoNegocio.RESTAURANTE);
        negocio.setDireccion("Calle 49 # 50-21");
        negocio.setMunicipio(Municipio.RIONEGRO);
        usuario.asignarNegocio(negocio);
        return usuario;
    }

    private Oferta publicar(Usuario dueno, ModalidadLogistica modalidad) {
        Oferta nueva = new Oferta();
        nueva.setProductor(dueno);
        nueva.setFinca(dueno.getFinca());
        nueva.setProducto(Producto.TOMATE_CHONTO);
        nueva.setCantidadDisponible(new BigDecimal("250.00"));
        nueva.setUnidadMedida(UnidadMedida.KILOGRAMO);
        nueva.setPrecioUnitario(new BigDecimal("3500.00"));
        nueva.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        nueva.setModalidadLogistica(modalidad);
        return ofertaRepository.saveAndFlush(nueva);
    }

    private CrearPedidoRequest.CrearPedidoRequestBuilder requestValido() {
        return CrearPedidoRequest.builder()
                .ofertaId(oferta.getId())
                .cantidadSolicitada(new BigDecimal("120.00"))
                .direccionEntrega("Calle 49 # 50-21, local 3")
                .municipioEntrega("Rionegro")
                .fechaPreferida(hoy.plusDays(3))
                .jornada(Jornada.MANANA)
                .modalidadEntrega(ModalidadLogistica.DESPACHO_PRODUCTOR)
                .notasAcceso("Entrada por la puerta de carga");
    }

    private MockHttpServletRequestBuilder postJson(Object cuerpo) throws Exception {
        return post(URL_PEDIDOS)
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

    private Pedido pedidoGuardado(Usuario dueno, Oferta ofertaPedida) {
        Pedido pedido = new Pedido();
        pedido.setOferta(ofertaPedida);
        pedido.setComprador(dueno);
        pedido.setCantidadSolicitada(new BigDecimal("10.00"));
        pedido.setPrecioUnitario(new BigDecimal("3500.00"));
        pedido.setPrecioTotalEstimado(new BigDecimal("35000.00"));
        pedido.setDireccionEntrega("Calle 49 # 50-21");
        pedido.setMunicipioEntrega(Municipio.RIONEGRO);
        pedido.setFechaPreferida(hoy.plusDays(2));
        pedido.setJornada(Jornada.TARDE);
        pedido.setModalidadEntrega(ModalidadLogistica.RECOGIDA_FINCA);
        return pedidoRepository.saveAndFlush(pedido);
    }

    // ---------- POST /api/pedidos ----------

    @Test
    @DisplayName("US-18 Esc 1 — crea el pedido SOLICITADO, responde 201 con Location y lo persiste")
    void testCrearPedidoExitoso() throws Exception {
        MvcResult resultado = mockMvc.perform(postJson(requestValido().build()).with(autenticadoComo(comprador)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.estado").value("SOLICITADO"))
                .andExpect(jsonPath("$.fechaCreacion").isNotEmpty())
                .andExpect(jsonPath("$.cantidadSolicitada").value(120.00))
                .andExpect(jsonPath("$.precioUnitario").value(3500.00))
                .andExpect(jsonPath("$.precioTotalEstimado").value(420000.00))
                .andExpect(jsonPath("$.direccionEntrega").value("Calle 49 # 50-21, local 3"))
                .andExpect(jsonPath("$.municipioEntrega").value("RIONEGRO"))
                .andExpect(jsonPath("$.fechaPreferida").value(hoy.plusDays(3).toString()))
                .andExpect(jsonPath("$.jornada").value("MANANA"))
                .andExpect(jsonPath("$.modalidadEntrega").value("DESPACHO_PRODUCTOR"))
                .andExpect(jsonPath("$.notasAcceso").value("Entrada por la puerta de carga"))
                .andExpect(jsonPath("$.oferta.id").value(oferta.getId().toString()))
                .andExpect(jsonPath("$.oferta.producto").value("TOMATE_CHONTO"))
                .andExpect(jsonPath("$.oferta.unidadMedida").value("KILOGRAMO"))
                .andExpect(jsonPath("$.oferta.fincaNombre").value("Finca El Roble"))
                .andExpect(jsonPath("$.oferta.municipio").value("SONSON"))
                .andExpect(jsonPath("$.productor.nombreCompleto").value("María Gómez"))
                .andExpect(jsonPath("$.productor.whatsappDisponible").value(true))
                .andExpect(jsonPath("$.productor.llamadaDisponible").value(false))
                .andExpect(jsonPath("$.comprador.nombreNegocio").value("Restaurante El Fogón"))
                .andReturn();

        String id = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asText();
        assertTrue(resultado.getResponse().getHeader(HttpHeaders.LOCATION).endsWith(URL_PEDIDOS + "/" + id));

        entityManager.flush();
        entityManager.clear();
        Pedido guardado = pedidoRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(EstadoPedido.SOLICITADO, guardado.getEstado());
        assertEquals(comprador.getId(), guardado.getComprador().getId());
        assertEquals(oferta.getId(), guardado.getOferta().getId());
        assertEquals(0, new BigDecimal("420000.00").compareTo(guardado.getPrecioTotalEstimado()));
        // El pedido no descuenta inventario
        Oferta sinCambios = ofertaRepository.findById(oferta.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("250.00").compareTo(sinCambios.getCantidadDisponible()));
        assertTrue(sinCambios.isActiva());
    }

    @Test
    @DisplayName("US-18 Esc 2 — sin dirección de entrega responde 400 en errores.direccionEntrega")
    void testCrearPedidoSinDireccion() throws Exception {
        mockMvc.perform(postJson(requestValido().direccionEntrega("   ").build()).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(GlobalExceptionHandler.MENSAJE_CAMPOS_INVALIDOS))
                .andExpect(jsonPath("$.errores.direccionEntrega").value(CrearPedidoRequest.MENSAJE_DIRECCION_REQUERIDA));

        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @DisplayName("US-18 Esc 3 — sin jornada responde 400 en errores.jornada")
    void testCrearPedidoSinJornada() throws Exception {
        mockMvc.perform(postJson(requestValido().jornada(null).build()).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(GlobalExceptionHandler.MENSAJE_CAMPOS_INVALIDOS))
                .andExpect(jsonPath("$.errores.jornada").value(CrearPedidoRequest.MENSAJE_JORNADA_REQUERIDA));

        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @DisplayName("US-18 Regla — los obligatorios faltantes responden juntos en errores")
    void testCrearPedidoObligatoriosFaltantes() throws Exception {
        mockMvc.perform(postJson(CrearPedidoRequest.builder().build()).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.ofertaId").value(CrearPedidoRequest.MENSAJE_OFERTA_REQUERIDA))
                .andExpect(jsonPath("$.errores.cantidadSolicitada").value(CrearPedidoRequest.MENSAJE_CANTIDAD_REQUERIDA))
                .andExpect(jsonPath("$.errores.direccionEntrega").value(CrearPedidoRequest.MENSAJE_DIRECCION_REQUERIDA))
                .andExpect(jsonPath("$.errores.municipioEntrega").value(CrearPedidoRequest.MENSAJE_MUNICIPIO_REQUERIDO))
                .andExpect(jsonPath("$.errores.fechaPreferida").value(CrearPedidoRequest.MENSAJE_FECHA_REQUERIDA))
                .andExpect(jsonPath("$.errores.jornada").value(CrearPedidoRequest.MENSAJE_JORNADA_REQUERIDA));
    }

    @Test
    @DisplayName("US-18 Regla — una cantidad de cero responde 400 en errores.cantidadSolicitada")
    void testCrearPedidoCantidadCero() throws Exception {
        mockMvc.perform(postJson(requestValido().cantidadSolicitada(BigDecimal.ZERO).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cantidadSolicitada").value(CrearPedidoRequest.MENSAJE_CANTIDAD_POSITIVA));
    }

    @Test
    @DisplayName("US-18 Esc 5 — 250 disponibles y piden 300: 400 en errores.cantidadSolicitada sin guardar nada")
    void testCrearPedidoCantidadMayorADisponible() throws Exception {
        mockMvc.perform(postJson(requestValido().cantidadSolicitada(new BigDecimal("300")).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(GlobalExceptionHandler.MENSAJE_CAMPOS_INVALIDOS))
                .andExpect(jsonPath("$.errores.cantidadSolicitada")
                        .value(PedidoService.MENSAJE_CANTIDAD_SUPERA_DISPONIBLE));

        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @DisplayName("US-18 Regla — una fecha preferida anterior a hoy responde en errores.fechaPreferida")
    void testCrearPedidoFechaPasada() throws Exception {
        mockMvc.perform(postJson(requestValido().fechaPreferida(hoy.minusDays(1)).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaPreferida").value(PedidoService.MENSAJE_FECHA_ANTERIOR_A_HOY));

        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @DisplayName("US-18 Regla — en una oferta EN_CRECIMIENTO la fecha anterior a la cosecha responde en errores.fechaPreferida")
    void testCrearPedidoFechaAntesDeCosecha() throws Exception {
        oferta.setEstadoCultivo(EstadoCultivo.EN_CRECIMIENTO);
        oferta.setFechaEstimadaCosecha(hoy.plusDays(10));
        ofertaRepository.saveAndFlush(oferta);

        mockMvc.perform(postJson(requestValido().fechaPreferida(hoy.plusDays(5)).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaPreferida").value(PedidoService.MENSAJE_FECHA_ANTERIOR_A_COSECHA));

        mockMvc.perform(postJson(requestValido().fechaPreferida(hoy.plusDays(10)).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("US-18 Regla — un municipio fuera del catálogo responde en errores.municipioEntrega")
    void testCrearPedidoMunicipioInvalido() throws Exception {
        mockMvc.perform(postJson(requestValido().municipioEntrega("Bogotá").build()).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.municipioEntrega").value(PedidoService.MENSAJE_MUNICIPIO_INVALIDO));
    }

    @Test
    @DisplayName("US-18 Regla — si la oferta es AMBAS sin modalidad responde en errores.modalidadEntrega")
    void testCrearPedidoAmbasSinModalidad() throws Exception {
        mockMvc.perform(postJson(requestValido().modalidadEntrega(null).build()).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.modalidadEntrega").value(PedidoService.MENSAJE_MODALIDAD_REQUERIDA));
    }

    @Test
    @DisplayName("US-18 Regla — AMBAS o una modalidad no habilitada responde en errores.modalidadEntrega")
    void testCrearPedidoModalidadNoHabilitada() throws Exception {
        mockMvc.perform(postJson(requestValido().modalidadEntrega(ModalidadLogistica.AMBAS).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.modalidadEntrega").value(PedidoService.MENSAJE_MODALIDAD_NO_HABILITADA));

        Oferta soloRecogida = publicar(productor, ModalidadLogistica.RECOGIDA_FINCA);
        mockMvc.perform(postJson(requestValido().ofertaId(soloRecogida.getId()).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.modalidadEntrega").value(PedidoService.MENSAJE_MODALIDAD_NO_HABILITADA));

        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @DisplayName("US-18 Regla — si la oferta tiene una sola modalidad y no se envía se fija la de la oferta")
    void testCrearPedidoModalidadFijada() throws Exception {
        Oferta soloRecogida = publicar(productor, ModalidadLogistica.RECOGIDA_FINCA);

        mockMvc.perform(postJson(requestValido().ofertaId(soloRecogida.getId()).modalidadEntrega(null).build())
                        .with(autenticadoComo(comprador)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modalidadEntrega").value("RECOGIDA_FINCA"));
    }

    @Test
    @DisplayName("US-18 Regla — varios errores de negocio llegan juntos en una sola respuesta")
    void testCrearPedidoVariosErrores() throws Exception {
        CrearPedidoRequest request = requestValido()
                .cantidadSolicitada(new BigDecimal("300"))
                .fechaPreferida(hoy.minusDays(1))
                .modalidadEntrega(null)
                .build();

        mockMvc.perform(postJson(request).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cantidadSolicitada").value(PedidoService.MENSAJE_CANTIDAD_SUPERA_DISPONIBLE))
                .andExpect(jsonPath("$.errores.fechaPreferida").value(PedidoService.MENSAJE_FECHA_ANTERIOR_A_HOY))
                .andExpect(jsonPath("$.errores.modalidadEntrega").value(PedidoService.MENSAJE_MODALIDAD_REQUERIDA));
    }

    @Test
    @DisplayName("US-18 Regla — una oferta agotada responde 422 con el mensaje exacto")
    void testCrearPedidoOfertaAgotada() throws Exception {
        oferta.setActiva(false);
        ofertaRepository.saveAndFlush(oferta);

        mockMvc.perform(postJson(requestValido().build()).with(autenticadoComo(comprador)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(PedidoService.MENSAJE_OFERTA_AGOTADA))
                .andExpect(jsonPath("$.errores").doesNotExist());

        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @DisplayName("US-18 Regla — una oferta inexistente responde 404")
    void testCrearPedidoOfertaInexistente() throws Exception {
        mockMvc.perform(postJson(requestValido().ofertaId(UUID.randomUUID()).build()).with(autenticadoComo(comprador)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(PedidoService.MENSAJE_OFERTA_NO_ENCONTRADA));
    }

    @Test
    @DisplayName("US-18 Regla — un productor no puede crear pedidos (403)")
    void testCrearPedidoComoProductor() throws Exception {
        mockMvc.perform(postJson(requestValido().build()).with(autenticadoComo(productor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(0, pedidoRepository.count());
    }

    @Test
    @DisplayName("US-18 Regla — crear un pedido sin token responde 401")
    void testCrearPedidoSinToken() throws Exception {
        mockMvc.perform(postJson(requestValido().build()))
                .andExpect(status().isUnauthorized());

        assertEquals(0, pedidoRepository.count());
    }

    // ---------- GET /api/pedidos/{id} ----------

    @Test
    @DisplayName("US-18 Regla — el comprador dueño ve su pedido con los números del productor según sus canales")
    void testObtenerPedidoComprador() throws Exception {
        Pedido pedido = pedidoGuardado(comprador, oferta);

        mockMvc.perform(get(URL_PEDIDOS + "/" + pedido.getId()).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedido.getId().toString()))
                .andExpect(jsonPath("$.estado").value("SOLICITADO"))
                .andExpect(jsonPath("$.jornada").value("TARDE"))
                .andExpect(jsonPath("$.productor.nombreCompleto").value("María Gómez"))
                .andExpect(jsonPath("$.productor.whatsappDisponible").value(true))
                .andExpect(jsonPath("$.productor.whatsappContacto").value("+573109876543"))
                .andExpect(jsonPath("$.productor.llamadaDisponible").value(false))
                .andExpect(jsonPath("$.productor.telefonoContacto").isEmpty())
                .andExpect(jsonPath("$.productor.correo").doesNotExist());
    }

    @Test
    @DisplayName("US-18 Regla — el productor de la oferta ve el pedido con los datos del comprador")
    void testObtenerPedidoProductor() throws Exception {
        Pedido pedido = pedidoGuardado(comprador, oferta);

        String cuerpo = mockMvc.perform(get(URL_PEDIDOS + "/" + pedido.getId()).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprador.nombre").value("Laura Restrepo"))
                .andExpect(jsonPath("$.comprador.nombreNegocio").value("Restaurante El Fogón"))
                .andExpect(jsonPath("$.comprador.whatsapp").value("+573201112233"))
                .andExpect(jsonPath("$.comprador.telefono").value("+576045551234"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(cuerpo.contains("compras@elfogon.com"), "El pedido expone el correo del comprador: " + cuerpo);
        assertFalse(cuerpo.contains("maria@finca.com"), "El pedido expone el correo del productor: " + cuerpo);
    }

    @Test
    @DisplayName("US-18 Regla — otro comprador recibe 403 al consultar un pedido ajeno")
    void testObtenerPedidoOtroComprador() throws Exception {
        Pedido pedido = pedidoGuardado(comprador, oferta);
        Usuario otroComprador = usuarioRepository.save(nuevoComprador("compras@laparrilla.com", "La Parrilla"));

        mockMvc.perform(get(URL_PEDIDOS + "/" + pedido.getId()).with(autenticadoComo(otroComprador)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(PedidoService.MENSAJE_PEDIDO_AJENO));
    }

    @Test
    @DisplayName("US-18 Regla — otro productor recibe 403 al consultar un pedido de una oferta ajena")
    void testObtenerPedidoOtroProductor() throws Exception {
        Pedido pedido = pedidoGuardado(comprador, oferta);
        Usuario otroProductor = usuarioRepository.save(nuevoProductor("andres@finca.com", "Finca La Palma"));

        mockMvc.perform(get(URL_PEDIDOS + "/" + pedido.getId()).with(autenticadoComo(otroProductor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(PedidoService.MENSAJE_PEDIDO_AJENO));
    }

    @Test
    @DisplayName("US-18 Regla — un pedido inexistente responde 404")
    void testObtenerPedidoInexistente() throws Exception {
        mockMvc.perform(get(URL_PEDIDOS + "/" + UUID.randomUUID()).with(autenticadoComo(comprador)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(PedidoService.MENSAJE_PEDIDO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("US-18 Regla — consultar un pedido sin token responde 401")
    void testObtenerPedidoSinToken() throws Exception {
        Pedido pedido = pedidoGuardado(comprador, oferta);

        mockMvc.perform(get(URL_PEDIDOS + "/" + pedido.getId()))
                .andExpect(status().isUnauthorized());
    }
}
