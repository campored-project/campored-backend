package com.campored.backend.controller;

import com.campored.backend.dto.ActualizarPerfilCompradorRequest;
import com.campored.backend.dto.ActualizarPerfilProductorRequest;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Negocio;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.TipoNegocio;
import com.campored.backend.entity.Usuario;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.service.JwtService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("UsuarioController - /api/usuarios/perfil")
class UsuarioControllerIntegrationTest {

    private static final String URL_PERFIL_PRODUCTOR = "/api/usuarios/perfil/productor";
    private static final String URL_PERFIL_COMPRADOR = "/api/usuarios/perfil/comprador";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    private Usuario productor;
    private Usuario comprador;

    @BeforeEach
    void setUp() {
        productor = usuarioRepository.save(nuevoProductor());
        comprador = usuarioRepository.save(nuevoComprador());
    }

    private Usuario nuevoProductor() {
        Usuario usuario = new Usuario();
        usuario.setNombre("María Gómez");
        usuario.setCorreo("maria@finca.com");
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.PRODUCTOR);
        usuario.setTelefono("+573001234567");
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
        usuario.setContrasenaHash("hash-no-usado-en-estas-pruebas");
        usuario.setRol(Rol.COMPRADOR);
        Negocio negocio = new Negocio();
        negocio.setNombreNegocio("Restaurante El Fogón");
        negocio.setTipoNegocio(TipoNegocio.RESTAURANTE);
        negocio.setDireccion("Calle 49 # 50-21");
        negocio.setMunicipio(Municipio.RIONEGRO);
        negocio.setNotasAcceso("Entrada de proveedores por la parte trasera");
        usuario.asignarNegocio(negocio);
        return usuario;
    }

    private MockHttpServletRequestBuilder patchJson(String url, Object cuerpo) throws Exception {
        return patch(url)
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
    @DisplayName("Debe actualizar el perfil del productor y devolver 200")
    void testActualizarPerfilProductorExitoso() throws Exception {
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .telefono("+573109998877")
                .whatsapp("+573105556644")
                .canalLlamadaHabilitado(true)
                .canalWhatsappHabilitado(true)
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, request).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productor.getId().toString()))
                .andExpect(jsonPath("$.telefono").value("+573109998877"))
                .andExpect(jsonPath("$.whatsapp").value("+573105556644"))
                .andExpect(jsonPath("$.canalLlamadaHabilitado").value(true))
                .andExpect(jsonPath("$.canalWhatsappHabilitado").value(true))
                .andExpect(jsonPath("$.nombreFinca").value("Finca El Roble"))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());

        Usuario actualizado = usuarioRepository.findById(productor.getId()).orElseThrow();
        assertEquals("+573105556644", actualizado.getWhatsapp());
        assertTrue(actualizado.isCanalWhatsappHabilitado());
    }

    @Test
    @DisplayName("Debe habilitar el canal de llamadas con el teléfono ya registrado")
    void testActualizarPerfilProductorParcial() throws Exception {
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .canalLlamadaHabilitado(true)
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, request).with(autenticadoComo(productor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("+573001234567"))
                .andExpect(jsonPath("$.canalLlamadaHabilitado").value(true))
                .andExpect(jsonPath("$.canalWhatsappHabilitado").value(false));
    }

    @Test
    @DisplayName("Debe devolver 401 al actualizar el perfil del productor sin token")
    void testActualizarPerfilProductorSinToken() throws Exception {
        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, new ActualizarPerfilProductorRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe devolver 403 si un comprador intenta actualizar el perfil de productor")
    void testActualizarPerfilProductorConTokenDeComprador() throws Exception {
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .telefono("+573109998877")
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, request).with(autenticadoComo(comprador)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertNull(usuarioRepository.findById(comprador.getId()).orElseThrow().getTelefono());
    }

    @Test
    @DisplayName("Debe devolver 400 si se habilita un canal y se borra su número")
    void testActualizarPerfilProductorCanalesSinNumero() throws Exception {
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .telefono("")
                .whatsapp("")
                .canalLlamadaHabilitado(true)
                .canalWhatsappHabilitado(true)
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, request).with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.telefono")
                        .value(ActualizarPerfilProductorRequest.MENSAJE_TELEFONO_REQUERIDO))
                .andExpect(jsonPath("$.errores.whatsapp")
                        .value(ActualizarPerfilProductorRequest.MENSAJE_WHATSAPP_REQUERIDO));
    }

    @Test
    @DisplayName("Debe devolver 400 si se habilita WhatsApp y el productor no tiene número guardado")
    void testActualizarPerfilProductorWhatsappSinNumeroGuardado() throws Exception {
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .canalWhatsappHabilitado(true)
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, request).with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(ActualizarPerfilProductorRequest.MENSAJE_WHATSAPP_REQUERIDO));
    }

    @Test
    @DisplayName("Debe devolver 400 si el teléfono del productor no tiene un formato válido")
    void testActualizarPerfilProductorTelefonoInvalido() throws Exception {
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .telefono("123")
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, request).with(autenticadoComo(productor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.telefono").value("El teléfono debe tener entre 10 y 13 dígitos"));
    }

    @Test
    @DisplayName("Debe devolver 404 si el token pertenece a un usuario que ya no existe")
    void testActualizarPerfilUsuarioInexistente() throws Exception {
        Usuario eliminado = new Usuario();
        eliminado.setId(UUID.randomUUID());
        eliminado.setCorreo("eliminado@finca.com");
        eliminado.setRol(Rol.PRODUCTOR);

        mockMvc.perform(patchJson(URL_PERFIL_PRODUCTOR, new ActualizarPerfilProductorRequest())
                        .with(autenticadoComo(eliminado)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Perfil de usuario no encontrado"));
    }

    @Test
    @DisplayName("Debe actualizar el perfil del comprador y devolver 200")
    void testActualizarPerfilCompradorExitoso() throws Exception {
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .direccion("Carrera 50 # 45-10, local 2")
                .municipio("La Ceja")
                .horarioRecepcion("Lunes a viernes, 5:00 a 9:00 a. m.")
                .notasAcceso("")
                .telefono("+573009876543")
                .whatsapp("+573009876543")
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_COMPRADOR, request).with(autenticadoComo(comprador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(comprador.getId().toString()))
                .andExpect(jsonPath("$.direccion").value("Carrera 50 # 45-10, local 2"))
                .andExpect(jsonPath("$.municipio").value("LA_CEJA"))
                .andExpect(jsonPath("$.horarioRecepcion").value("Lunes a viernes, 5:00 a 9:00 a. m."))
                .andExpect(jsonPath("$.notasAcceso").isEmpty())
                .andExpect(jsonPath("$.telefono").value("+573009876543"))
                .andExpect(jsonPath("$.whatsapp").value("+573009876543"))
                .andExpect(jsonPath("$.nombreNegocio").value("Restaurante El Fogón"))
                .andExpect(jsonPath("$.tipoNegocio").value("RESTAURANTE"));

        Negocio negocio = usuarioRepository.findById(comprador.getId()).orElseThrow().getNegocio();
        assertEquals(Municipio.LA_CEJA, negocio.getMunicipio());
        assertNull(negocio.getNotasAcceso());
    }

    @Test
    @DisplayName("Debe devolver 401 al actualizar el perfil del comprador sin token")
    void testActualizarPerfilCompradorSinToken() throws Exception {
        mockMvc.perform(patchJson(URL_PERFIL_COMPRADOR, new ActualizarPerfilCompradorRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe devolver 403 si un productor intenta actualizar el perfil de comprador")
    void testActualizarPerfilCompradorConTokenDeProductor() throws Exception {
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .direccion("Carrera 50 # 45-10")
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_COMPRADOR, request).with(autenticadoComo(productor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Debe devolver 400 si la dirección del comprador queda vacía")
    void testActualizarPerfilCompradorDireccionVacia() throws Exception {
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .direccion("   ")
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_COMPRADOR, request).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.direccion")
                        .value(ActualizarPerfilCompradorRequest.MENSAJE_DIRECCION_VACIA));

        assertEquals("Calle 49 # 50-21",
                usuarioRepository.findById(comprador.getId()).orElseThrow().getNegocio().getDireccion());
    }

    @Test
    @DisplayName("Debe devolver 400 si el municipio del comprador no está soportado")
    void testActualizarPerfilCompradorMunicipioInvalido() throws Exception {
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .municipio("Bogotá")
                .build();

        mockMvc.perform(patchJson(URL_PERFIL_COMPRADOR, request).with(autenticadoComo(comprador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("Municipio no soportado")));
    }
}
