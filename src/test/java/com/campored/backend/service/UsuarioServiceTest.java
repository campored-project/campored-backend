package com.campored.backend.service;

import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.exception.ResourceAlreadyExistsException;
import com.campored.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UsuarioService - Registro de Productor")
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private RegistroProductorRequest requestValido() {
        return new RegistroProductorRequest(
                "juan@finca.com",
                "SecurePass123",
                "Juan Pérez",
                "Finca La Esperanza",
                "El Sauce",
                "SONSON",
                "+573001234567",
                "+573001234567"
        );
    }

    @Test
    @DisplayName("Debe registrar un productor con su finca y contraseña encriptada")
    void testRegistrarProductorExitoso() {
        RegistroProductorRequest request = requestValido();
        when(usuarioRepository.existsByCorreo("juan@finca.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hashed_password");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            usuario.setId(UUID.randomUUID());
            return usuario;
        });

        Usuario resultado = usuarioService.registrarProductor(request);

        assertNotNull(resultado.getId());
        assertEquals("juan@finca.com", resultado.getCorreo());
        assertEquals("hashed_password", resultado.getContrasenaHash());
        assertEquals(Rol.PRODUCTOR, resultado.getRol());
        assertEquals("+573001234567", resultado.getTelefono());
        assertEquals("Finca La Esperanza", resultado.getFinca().getNombreFinca());
        assertEquals(Municipio.SONSON, resultado.getFinca().getMunicipio());
        assertEquals("El Sauce", resultado.getFinca().getVereda());
        assertSame(resultado, resultado.getFinca().getProductor());

        verify(usuarioRepository, times(1)).existsByCorreo("juan@finca.com");
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el correo ya existe")
    void testRegistrarProductorConCorreoDuplicado() {
        RegistroProductorRequest request = requestValido();
        when(usuarioRepository.existsByCorreo(request.getCorreo())).thenReturn(true);

        ResourceAlreadyExistsException ex = assertThrows(ResourceAlreadyExistsException.class,
                () -> usuarioService.registrarProductor(request));

        assertEquals("Este correo ya está registrado", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Debe rechazar un municipio que no está soportado")
    void testRegistrarProductorConMunicipioInvalido() {
        RegistroProductorRequest request = requestValido();
        request.setMunicipio("BOGOTA");
        when(usuarioRepository.existsByCorreo(request.getCorreo())).thenReturn(false);

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> usuarioService.registrarProductor(request));

        assertTrue(ex.getMessage().contains("BOGOTA"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe normalizar el correo y aceptar el nombre visible del municipio")
    void testRegistrarProductorNormalizaDatos() {
        RegistroProductorRequest request = requestValido();
        request.setCorreo("  Juan@Finca.COM ");
        request.setMunicipio("El Carmen de Viboral");
        request.setNombre("  Juan Pérez  ");
        when(usuarioRepository.existsByCorreo("juan@finca.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        usuarioService.registrarProductor(request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals("juan@finca.com", guardado.getCorreo());
        assertEquals("Juan Pérez", guardado.getNombre());
        assertEquals(Municipio.EL_CARMEN_DE_VIBORAL, guardado.getFinca().getMunicipio());
    }
}
