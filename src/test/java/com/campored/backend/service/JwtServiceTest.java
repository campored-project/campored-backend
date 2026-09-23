package com.campored.backend.service;

import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JwtService - Emisión y validación de tokens")
class JwtServiceTest {

    private static final String SECRETO = "ClaveDePruebaParaTestsDeCampoRedConLongitudSuficienteDe256Bits";

    private Usuario productor() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setCorreo("juan@finca.com");
        usuario.setRol(Rol.PRODUCTOR);
        return usuario;
    }

    @Test
    @DisplayName("Debe generar un token con correo, rol e id del usuario")
    void testGenerarYValidarToken() {
        JwtService jwtService = new JwtService(SECRETO, 60_000);
        Usuario usuario = productor();

        Claims claims = jwtService.validarToken(jwtService.generarToken(usuario));

        assertEquals("juan@finca.com", claims.getSubject());
        assertEquals("PRODUCTOR", claims.get(JwtService.CLAIM_ROL, String.class));
        assertEquals(usuario.getId().toString(), claims.get(JwtService.CLAIM_USUARIO_ID, String.class));
        assertEquals(60_000, jwtService.getExpiracionMs());
    }

    @Test
    @DisplayName("Debe rechazar un token expirado")
    void testTokenExpirado() {
        JwtService jwtService = new JwtService(SECRETO, -1_000);
        String token = jwtService.generarToken(productor());

        assertThrows(ExpiredJwtException.class, () -> jwtService.validarToken(token));
    }

    @Test
    @DisplayName("Debe rechazar un token firmado con otra clave")
    void testTokenConFirmaAjena() {
        JwtService emisorAjeno = new JwtService(SECRETO + "Distinta", 60_000);
        JwtService jwtService = new JwtService(SECRETO, 60_000);
        String token = emisorAjeno.generarToken(productor());

        assertThrows(SignatureException.class, () -> jwtService.validarToken(token));
    }

    @Test
    @DisplayName("Debe impedir el arranque con un secreto menor a 256 bits")
    void testSecretoDebil() {
        assertThrows(WeakKeyException.class, () -> new JwtService("corta", 60_000));
    }
}
