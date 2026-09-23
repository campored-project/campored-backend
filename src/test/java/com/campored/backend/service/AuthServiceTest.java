package com.campored.backend.service;

import com.campored.backend.dto.AuthResponse;
import com.campored.backend.dto.LoginRequest;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.CredencialesInvalidasException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthService - Login de Productor")
class AuthServiceTest {

    private static final String HASH_FICTICIO = "hash_ficticio";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(passwordEncoder.encode(any())).thenReturn(HASH_FICTICIO);
        authService = new AuthService(usuarioService, jwtService, passwordEncoder);

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNombre("Juan Pérez");
        usuario.setCorreo("juan@finca.com");
        usuario.setContrasenaHash("hashed_password");
        usuario.setRol(Rol.PRODUCTOR);
    }

    @Test
    @DisplayName("Debe devolver el JWT y los datos del usuario con credenciales válidas")
    void testLoginExitoso() {
        LoginRequest request = new LoginRequest("juan@finca.com", "SecurePass123");
        when(usuarioService.buscarPorCorreo("juan@finca.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SecurePass123", "hashed_password")).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("jwt.token.firmado");
        when(jwtService.getExpiracionMs()).thenReturn(3600000L);

        AuthResponse respuesta = authService.login(request);

        assertEquals("jwt.token.firmado", respuesta.getToken());
        assertEquals("Bearer", respuesta.getTipo());
        assertEquals(3600000L, respuesta.getExpiraEnMs());
        assertEquals(usuario.getId(), respuesta.getUsuario().getId());
        assertEquals("juan@finca.com", respuesta.getUsuario().getCorreo());
        assertEquals(Rol.PRODUCTOR, respuesta.getUsuario().getRol());
    }

    @Test
    @DisplayName("Debe lanzar CredencialesInvalidasException si el correo no existe")
    void testLoginCorreoNoExiste() {
        LoginRequest request = new LoginRequest("nadie@finca.com", "SecurePass123");
        when(usuarioService.buscarPorCorreo("nadie@finca.com")).thenReturn(Optional.empty());

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(request));

        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(passwordEncoder).matches("SecurePass123", HASH_FICTICIO);
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    @DisplayName("Debe lanzar CredencialesInvalidasException si la contraseña no coincide")
    void testLoginContrasenaIncorrecta() {
        LoginRequest request = new LoginRequest("juan@finca.com", "OtraClave123");
        when(usuarioService.buscarPorCorreo("juan@finca.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("OtraClave123", "hashed_password")).thenReturn(false);

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(request));

        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(jwtService, never()).generarToken(any());
    }
}
