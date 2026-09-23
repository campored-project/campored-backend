package com.campored.backend.controller;

import com.campored.backend.dto.LoginRequest;
import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Usuario;
import com.campored.backend.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthController - /api/auth")
class AuthControllerIntegrationTest {

    private static final String URL_REGISTRO = "/api/auth/registro/productor";
    private static final String URL_LOGIN = "/api/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RegistroProductorRequest request;

    @BeforeEach
    void setUp() {
        request = new RegistroProductorRequest(
                "maria@finca.com",
                "SecurePass123",
                "María Gómez",
                "Finca El Roble",
                "La Honda",
                "SONSON",
                "+573001234567",
                null
        );
    }

    private MvcResult registrar(RegistroProductorRequest body) throws Exception {
        return mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    @Test
    @DisplayName("Debe registrar al productor y devolver 201 con JWT")
    void testRegistroExitoso() throws Exception {
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnMs").value(3600000))
                .andExpect(jsonPath("$.usuario.id", notNullValue()))
                .andExpect(jsonPath("$.usuario.correo").value("maria@finca.com"))
                .andExpect(jsonPath("$.usuario.rol").value("PRODUCTOR"))
                .andExpect(jsonPath("$.usuario.nombreFinca").value("Finca El Roble"))
                .andExpect(jsonPath("$.usuario.municipio").value("SONSON"))
                .andExpect(jsonPath("$.usuario.contrasenaHash").doesNotExist());

        Usuario guardado = usuarioRepository.findByCorreo("maria@finca.com").orElseThrow();
        assertNotEquals("SecurePass123", guardado.getContrasenaHash());
        assertTrue(passwordEncoder.matches("SecurePass123", guardado.getContrasenaHash()));
        assertEquals(Municipio.SONSON, guardado.getFinca().getMunicipio());
    }

    @Test
    @DisplayName("Debe devolver 409 si el correo ya está registrado")
    void testRegistroCorreoDuplicado() throws Exception {
        assertEquals(201, registrar(request).getResponse().getStatus());

        request.setCorreo("MARIA@finca.com");
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Este correo ya está registrado"));
    }

    @Test
    @DisplayName("Debe devolver 400 con el detalle de cada campo inválido")
    void testRegistroCamposInvalidos() throws Exception {
        RegistroProductorRequest invalido = new RegistroProductorRequest(
                "no-es-correo", "corta", "", "", "", "", "123", null);

        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo", notNullValue()))
                .andExpect(jsonPath("$.errores.contrasena", notNullValue()))
                .andExpect(jsonPath("$.errores.nombre", notNullValue()))
                .andExpect(jsonPath("$.errores.nombreFinca", notNullValue()))
                .andExpect(jsonPath("$.errores.vereda", notNullValue()))
                .andExpect(jsonPath("$.errores.municipio", notNullValue()))
                .andExpect(jsonPath("$.errores.telefono", notNullValue()));
    }

    @Test
    @DisplayName("Debe devolver 400 si el municipio no está soportado")
    void testRegistroMunicipioInvalido() throws Exception {
        request.setMunicipio("Bogotá");

        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("Municipio no soportado")));
    }

    @Test
    @DisplayName("Debe devolver 400 si el cuerpo no es JSON válido")
    void testRegistroJsonMalformado() throws Exception {
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{correo:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la solicitud no es un JSON válido"));
    }

    @Test
    @DisplayName("Debe devolver 405 si se usa un método no soportado")
    void testRegistroMetodoNoSoportado() throws Exception {
        mockMvc.perform(get(URL_REGISTRO))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Debe devolver 401 en rutas protegidas sin token")
    void testRutaProtegidaSinToken() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe devolver 401 en rutas protegidas con token inválido")
    void testRutaProtegidaConTokenInvalido() throws Exception {
        mockMvc.perform(get("/api/productos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido.firma"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe autenticar rutas protegidas con el token emitido en el registro")
    void testRutaProtegidaConTokenValido() throws Exception {
        MvcResult resultado = registrar(request);
        String token = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("token").asText();

        // La ruta aún no existe: un 404 en lugar de 401 confirma que el filtro aceptó el token
        mockMvc.perform(get("/api/productos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Debe iniciar sesión y devolver 200 con JWT")
    void testLoginExitoso() throws Exception {
        assertEquals(201, registrar(request).getResponse().getStatus());
        LoginRequest login = new LoginRequest("MARIA@finca.com", "SecurePass123");

        mockMvc.perform(post(URL_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnMs").value(3600000))
                .andExpect(jsonPath("$.usuario.correo").value("maria@finca.com"))
                .andExpect(jsonPath("$.usuario.rol").value("PRODUCTOR"))
                .andExpect(jsonPath("$.usuario.contrasenaHash").doesNotExist());
    }

    @Test
    @DisplayName("Debe devolver 401 con el mismo mensaje si la contraseña o el correo son incorrectos")
    void testLoginCredencialesInvalidas() throws Exception {
        assertEquals(201, registrar(request).getResponse().getStatus());

        mockMvc.perform(post(URL_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("maria@finca.com", "OtraClave123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(post(URL_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nadie@finca.com", "SecurePass123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
    }

    @Test
    @DisplayName("Debe devolver 400 si el correo está vacío")
    void testLoginCorreoVacio() throws Exception {
        mockMvc.perform(post(URL_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", "SecurePass123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").value("El correo es obligatorio"));
    }
}
