package com.campored.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CORS - Integración con el frontend")
class CorsIntegrationTest {

    private static final String ORIGEN_FRONTEND = "http://localhost:5173";
    private static final String URL_LOGIN = "/api/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Debe aceptar el preflight del frontend con credenciales")
    void testPreflightDesdeFrontend() throws Exception {
        mockMvc.perform(options(URL_LOGIN)
                        .header(HttpHeaders.ORIGIN, ORIGEN_FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));
    }

    @Test
    @DisplayName("Debe rechazar el preflight de un origen no permitido")
    void testPreflightDesdeOrigenNoPermitido() throws Exception {
        mockMvc.perform(options(URL_LOGIN)
                        .header(HttpHeaders.ORIGIN, "https://sitio-ajeno.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("Debe incluir cabeceras CORS en el 401 para que el frontend pueda leerlo")
    void testRespuesta401IncluyeCabecerasCors() throws Exception {
        // Si CORS no corre dentro de la cadena de Spring Security, el 401 sale sin cabeceras
        mockMvc.perform(get("/api/productos").header(HttpHeaders.ORIGIN, ORIGEN_FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_FRONTEND));
    }
}
