package com.campored.backend.config;

import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.repository.UsuarioRepository;
import com.campored.backend.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.PropertyPlaceholderHelper;

import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Seguridad - Autenticación JWT y protección de contraseñas")
class SecurityIntegrationTest {

    private static final String URL_REGISTRO = "/api/auth/registro/productor";
    private static final String URL_PERFIL_PRODUCTOR = "/api/usuarios/perfil/productor";
    private static final String CORREO = "maria@finca.com";
    private static final String CONTRASENA = "SecurePass123";
    private static final Pattern FORMATO_BCRYPT = Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${jwt.secret}")
    private String secretoJwt;

    static Stream<Arguments> rutasProtegidas() {
        return Stream.of(
                Arguments.of(HttpMethod.PATCH, URL_PERFIL_PRODUCTOR),
                Arguments.of(HttpMethod.PATCH, "/api/usuarios/perfil/comprador"),
                Arguments.of(HttpMethod.GET, "/api/productos"));
    }

    private String registrarProductor() throws Exception {
        RegistroProductorRequest request = new RegistroProductorRequest(
                CORREO, CONTRASENA, "María Gómez", "Finca El Roble", "La Honda", "SONSON", "+573001234567", null);
        String cuerpo = mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asText();
    }

    private Usuario productorRegistrado() throws Exception {
        registrarProductor();
        return usuarioRepository.findByCorreo(CORREO).orElseThrow();
    }

    private ResultActions actualizarPerfil(String autorizacion) throws Exception {
        return mockMvc.perform(patch(URL_PERFIL_PRODUCTOR)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasProtegidas")
    @DisplayName("Debe devolver 401 en rutas protegidas sin cabecera Authorization")
    void testRutaProtegidaSinToken(HttpMethod metodo, String ruta) throws Exception {
        mockMvc.perform(request(metodo, ruta))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "Authorization: {0}")
    @ValueSource(strings = {"Bearer token.invalido.firma", "Bearer ", "Basic bWFyaWE6U2VjdXJlUGFzczEyMw==", "sin-prefijo"})
    @DisplayName("Debe devolver 401 con un token mal formado o un esquema distinto a Bearer")
    void testRutaProtegidaConTokenInvalido(String autorizacion) throws Exception {
        actualizarPerfil(autorizacion)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe devolver 401 con un token firmado con otra clave")
    void testTokenFirmadoConOtraClave() throws Exception {
        Usuario productor = productorRegistrado();
        JwtService emisorAjeno = new JwtService(secretoJwt + "Ajena", jwtService.getExpiracionMs());

        actualizarPerfil("Bearer " + emisorAjeno.generarToken(productor))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe devolver 401 con un token expirado aunque la firma sea válida")
    void testTokenExpirado() throws Exception {
        Usuario productor = productorRegistrado();
        // Misma clave y mismo usuario que el token vigente; solo cambia la fecha de expiración
        JwtService emisorVencido = new JwtService(secretoJwt, -Duration.ofMinutes(1).toMillis());

        actualizarPerfil("Bearer " + jwtService.generarToken(productor))
                .andExpect(status().isOk());
        actualizarPerfil("Bearer " + emisorVencido.generarToken(productor))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe devolver 401 con un token válido que no identifica al usuario")
    void testTokenSinIdentificadorDeUsuario() throws Exception {
        Usuario sinId = new Usuario();
        sinId.setCorreo(CORREO);
        sinId.setRol(Rol.PRODUCTOR);

        actualizarPerfil("Bearer " + jwtService.generarToken(sinId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe guardar la contraseña como hash BCrypt con costo mínimo de 10")
    void testContrasenaGuardadaConBcrypt() throws Exception {
        String hash = productorRegistrado().getContrasenaHash();

        Matcher formato = FORMATO_BCRYPT.matcher(hash);
        assertTrue(formato.matches(), "El hash no tiene formato BCrypt: " + hash);
        assertTrue(Integer.parseInt(formato.group(1)) >= 10, "Costo BCrypt menor a 10");
        assertNotEquals(CONTRASENA, hash);
        assertTrue(passwordEncoder.matches(CONTRASENA, hash));
    }

    @Test
    @DisplayName("Debe emitir un JWT cuyo payload incluye el rol y ningún dato sensible")
    void testPayloadDelJwtIncluyeRol() throws Exception {
        String token = registrarProductor();
        UUID usuarioId = usuarioRepository.findByCorreo(CORREO).orElseThrow().getId();

        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertEquals("PRODUCTOR", payload.get(JwtService.CLAIM_ROL).asText());
        assertEquals(usuarioId.toString(), payload.get(JwtService.CLAIM_USUARIO_ID).asText());
        assertEquals(CORREO, payload.get("sub").asText());
        assertEquals(jwtService.getExpiracionMs() / 1000, payload.get("exp").asLong() - payload.get("iat").asLong());

        Set<String> campos = new HashSet<>();
        payload.fieldNames().forEachRemaining(campos::add);
        assertEquals(Set.of("sub", "uid", "rol", "iat", "exp"), campos);
    }

    @Test
    @DisplayName("La configuración por defecto debe emitir tokens con vigencia de 24 horas")
    void testExpiracionPorDefectoDe24Horas() throws Exception {
        Properties propiedades = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        // Se resuelve solo el valor por defecto de ${JWT_EXPIRATION:...}, sin depender del entorno de la máquina
        String expiracion = new PropertyPlaceholderHelper("${", "}", ":", true)
                .replacePlaceholders(propiedades.getProperty("jwt.expiration"), variable -> null);

        assertEquals(Duration.ofHours(24).toMillis(), Long.parseLong(expiracion));
    }
}
