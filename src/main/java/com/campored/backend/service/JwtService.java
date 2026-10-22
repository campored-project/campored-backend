package com.campored.backend.service;

import com.campored.backend.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_USUARIO_ID = "uid";

    private final SecretKey clave;
    private final long expiracionMs;

    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiration}") long expiracionMs) {
        // hmacShaKeyFor rechaza claves de menos de 256 bits, así la app no arranca con un secreto débil
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMs = expiracionMs;
    }

    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(usuario.getCorreo())
                .claim(CLAIM_USUARIO_ID, usuario.getId() != null ? usuario.getId().toString() : null)
                .claim(CLAIM_ROL, usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expiracionMs))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validarToken(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extraerUsuarioId(Claims claims) {
        String usuarioId = claims.get(CLAIM_USUARIO_ID, String.class);
        if (usuarioId == null) {
            throw new MalformedJwtException("El token no identifica al usuario");
        }
        return UUID.fromString(usuarioId);
    }

    public long getExpiracionMs() {
        return expiracionMs;
    }
}
