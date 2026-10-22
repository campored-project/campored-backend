package com.campored.backend.service;

import com.campored.backend.dto.ActualizarPerfilCompradorRequest;
import com.campored.backend.dto.ActualizarPerfilProductorRequest;
import com.campored.backend.dto.RegistroCompradorRequest;
import com.campored.backend.dto.RegistroProductorRequest;
import com.campored.backend.dto.UsuarioResponse;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Negocio;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.TipoNegocio;
import com.campored.backend.entity.Usuario;
import com.campored.backend.exception.InvalidInputException;
import com.campored.backend.exception.ResourceAlreadyExistsException;
import com.campored.backend.exception.ResourceNotFoundException;
import com.campored.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UsuarioService - Registro y perfil de Productor y Comprador")
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

    private RegistroCompradorRequest compradorValido() {
        return RegistroCompradorRequest.builder()
                .correo("compras@elfogon.com")
                .contrasena("SecurePass123")
                .nombre("Laura Restrepo")
                .nombreNegocio("Restaurante El Fogón")
                .tipoNegocio("RESTAURANTE")
                .direccion("Calle 49 # 50-21")
                .municipio("RIONEGRO")
                .horarioRecepcion("Lunes a sábado, 6:00 a 10:00 a. m.")
                .notasAcceso("Entrada de proveedores por la parte trasera")
                .telefono("+573009876543")
                .build();
    }

    private void simularGuardadoConId() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            usuario.setId(UUID.randomUUID());
            return usuario;
        });
    }

    @Test
    @DisplayName("Debe registrar un comprador con su negocio")
    void testRegistrarCompradorExitoso() {
        RegistroCompradorRequest request = compradorValido();
        when(usuarioRepository.existsByCorreo("compras@elfogon.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hashed_password");
        simularGuardadoConId();

        Usuario resultado = usuarioService.registrarComprador(request);

        assertNotNull(resultado.getId());
        assertEquals("compras@elfogon.com", resultado.getCorreo());
        assertEquals("Laura Restrepo", resultado.getNombre());
        assertEquals("+573009876543", resultado.getTelefono());
        assertNull(resultado.getWhatsapp());
        assertNull(resultado.getFinca());
        assertEquals("Restaurante El Fogón", resultado.getNegocio().getNombreNegocio());
        assertEquals(TipoNegocio.RESTAURANTE, resultado.getNegocio().getTipoNegocio());
        assertEquals("Calle 49 # 50-21", resultado.getNegocio().getDireccion());
        assertEquals(Municipio.RIONEGRO, resultado.getNegocio().getMunicipio());
        assertEquals("Lunes a sábado, 6:00 a 10:00 a. m.", resultado.getNegocio().getHorarioRecepcion());
        assertEquals("Entrada de proveedores por la parte trasera", resultado.getNegocio().getNotasAcceso());
        assertSame(resultado, resultado.getNegocio().getUsuario());

        verify(usuarioRepository, times(1)).existsByCorreo("compras@elfogon.com");
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Debe asignar el rol COMPRADOR al registrar un comprador")
    void testRegistrarCompradorAsignaRol() {
        when(usuarioRepository.existsByCorreo(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        simularGuardadoConId();

        Usuario resultado = usuarioService.registrarComprador(compradorValido());

        assertEquals(Rol.COMPRADOR, resultado.getRol());
    }

    @Test
    @DisplayName("Debe guardar la contraseña del comprador encriptada con BCrypt")
    void testRegistrarCompradorEncriptaContrasena() {
        when(usuarioRepository.existsByCorreo(any())).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$10$hashDePrueba");
        simularGuardadoConId();

        Usuario resultado = usuarioService.registrarComprador(compradorValido());

        assertEquals("$2a$10$hashDePrueba", resultado.getContrasenaHash());
        verify(passwordEncoder, times(1)).encode("SecurePass123");
    }

    @Test
    @DisplayName("Debe lanzar excepción si el correo del comprador ya existe")
    void testRegistrarCompradorConCorreoDuplicado() {
        RegistroCompradorRequest request = compradorValido();
        request.setCorreo(" Compras@ElFogon.com ");
        when(usuarioRepository.existsByCorreo("compras@elfogon.com")).thenReturn(true);

        ResourceAlreadyExistsException ex = assertThrows(ResourceAlreadyExistsException.class,
                () -> usuarioService.registrarComprador(request));

        assertEquals("Este correo ya está registrado", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Debe rechazar un tipo de negocio que no está soportado")
    void testRegistrarCompradorConTipoNegocioInvalido() {
        RegistroCompradorRequest request = compradorValido();
        request.setTipoNegocio("FERRETERIA");
        when(usuarioRepository.existsByCorreo(any())).thenReturn(false);

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> usuarioService.registrarComprador(request));

        assertTrue(ex.getMessage().contains("FERRETERIA"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar un municipio del comprador que no está soportado")
    void testRegistrarCompradorConMunicipioInvalido() {
        RegistroCompradorRequest request = compradorValido();
        request.setMunicipio("CALI");
        when(usuarioRepository.existsByCorreo(any())).thenReturn(false);

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> usuarioService.registrarComprador(request));

        assertTrue(ex.getMessage().contains("CALI"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe normalizar el tipo de negocio y guardar como nulos los campos opcionales vacíos")
    void testRegistrarCompradorNormalizaDatos() {
        RegistroCompradorRequest request = compradorValido();
        request.setTipoNegocio("minimercado");
        request.setMunicipio("Medellín");
        request.setHorarioRecepcion("   ");
        request.setNotasAcceso(null);
        request.setTelefono(null);
        when(usuarioRepository.existsByCorreo(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        usuarioService.registrarComprador(request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals(TipoNegocio.MINIMERCADO, guardado.getNegocio().getTipoNegocio());
        assertEquals(Municipio.MEDELLIN, guardado.getNegocio().getMunicipio());
        assertNull(guardado.getNegocio().getHorarioRecepcion());
        assertNull(guardado.getNegocio().getNotasAcceso());
        assertNull(guardado.getTelefono());
    }

    private Usuario productorGuardado() {
        Usuario productor = new Usuario();
        productor.setId(UUID.randomUUID());
        productor.setNombre("Juan Pérez");
        productor.setCorreo("juan@finca.com");
        productor.setRol(Rol.PRODUCTOR);
        productor.setTelefono("+573001234567");
        Finca finca = new Finca();
        finca.setNombreFinca("Finca La Esperanza");
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("El Sauce");
        productor.asignarFinca(finca);
        return productor;
    }

    private Usuario compradorGuardado() {
        Usuario comprador = new Usuario();
        comprador.setId(UUID.randomUUID());
        comprador.setNombre("Laura Restrepo");
        comprador.setCorreo("compras@elfogon.com");
        comprador.setRol(Rol.COMPRADOR);
        Negocio negocio = new Negocio();
        negocio.setNombreNegocio("Restaurante El Fogón");
        negocio.setTipoNegocio(TipoNegocio.RESTAURANTE);
        negocio.setDireccion("Calle 49 # 50-21");
        negocio.setMunicipio(Municipio.RIONEGRO);
        negocio.setNotasAcceso("Entrada de proveedores por la parte trasera");
        comprador.asignarNegocio(negocio);
        return comprador;
    }

    private void simularBusqueda(Usuario usuario) {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    @DisplayName("Debe actualizar los números y canales de contacto del productor")
    void testActualizarPerfilProductorExitoso() {
        Usuario productor = productorGuardado();
        simularBusqueda(productor);
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .telefono("+573109998877")
                .whatsapp("+573105556644")
                .canalLlamadaHabilitado(true)
                .canalWhatsappHabilitado(true)
                .build();

        UsuarioResponse respuesta = usuarioService.actualizarPerfilProductor(productor.getId(), request);

        assertEquals("+573109998877", respuesta.getTelefono());
        assertEquals("+573105556644", respuesta.getWhatsapp());
        assertTrue(respuesta.isCanalLlamadaHabilitado());
        assertTrue(respuesta.isCanalWhatsappHabilitado());
        assertEquals("Finca La Esperanza", respuesta.getNombreFinca());
        verify(usuarioRepository, times(1)).save(productor);
    }

    @Test
    @DisplayName("Debe conservar los campos que no se envían en la actualización del productor")
    void testActualizarPerfilProductorParcial() {
        Usuario productor = productorGuardado();
        simularBusqueda(productor);
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .canalLlamadaHabilitado(true)
                .build();

        UsuarioResponse respuesta = usuarioService.actualizarPerfilProductor(productor.getId(), request);

        assertEquals("+573001234567", respuesta.getTelefono());
        assertNull(respuesta.getWhatsapp());
        assertTrue(respuesta.isCanalLlamadaHabilitado());
        assertFalse(respuesta.isCanalWhatsappHabilitado());
    }

    @Test
    @DisplayName("Debe rechazar habilitar el canal de llamadas sin teléfono")
    void testActualizarCanalLlamadaSinTelefono() {
        Usuario productor = productorGuardado();
        simularBusqueda(productor);
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .telefono("")
                .canalLlamadaHabilitado(true)
                .build();

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> usuarioService.actualizarPerfilProductor(productor.getId(), request));

        assertEquals(ActualizarPerfilProductorRequest.MENSAJE_TELEFONO_REQUERIDO, ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar habilitar el canal de WhatsApp sin número de WhatsApp")
    void testActualizarCanalWhatsappSinWhatsapp() {
        Usuario productor = productorGuardado();
        simularBusqueda(productor);
        ActualizarPerfilProductorRequest request = ActualizarPerfilProductorRequest.builder()
                .canalWhatsappHabilitado(true)
                .build();

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> usuarioService.actualizarPerfilProductor(productor.getId(), request));

        assertEquals(ActualizarPerfilProductorRequest.MENSAJE_WHATSAPP_REQUERIDO, ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si el usuario del token no existe")
    void testActualizarPerfilUsuarioInexistente() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());
        ActualizarPerfilProductorRequest request = new ActualizarPerfilProductorRequest();

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.actualizarPerfilProductor(usuarioId, request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("No debe actualizar el perfil de comprador de un usuario con otro rol")
    void testActualizarPerfilCompradorConRolDistinto() {
        Usuario productor = productorGuardado();
        simularBusqueda(productor);
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .direccion("Carrera 50 # 45-10")
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.actualizarPerfilComprador(productor.getId(), request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe actualizar los datos de entrega y contacto del comprador")
    void testActualizarPerfilCompradorExitoso() {
        Usuario comprador = compradorGuardado();
        simularBusqueda(comprador);
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .direccion("  Carrera 50 # 45-10, local 2 ")
                .municipio("La Ceja")
                .horarioRecepcion("Lunes a viernes, 5:00 a 9:00 a. m.")
                .notasAcceso("")
                .telefono("+573009876543")
                .whatsapp("+573009876543")
                .build();

        UsuarioResponse respuesta = usuarioService.actualizarPerfilComprador(comprador.getId(), request);

        assertEquals("Carrera 50 # 45-10, local 2", respuesta.getDireccion());
        assertEquals(Municipio.LA_CEJA, respuesta.getMunicipio());
        assertEquals("Lunes a viernes, 5:00 a 9:00 a. m.", respuesta.getHorarioRecepcion());
        assertNull(respuesta.getNotasAcceso());
        assertEquals("+573009876543", respuesta.getTelefono());
        assertEquals("+573009876543", respuesta.getWhatsapp());
        assertEquals("Restaurante El Fogón", respuesta.getNombreNegocio());
        assertEquals(TipoNegocio.RESTAURANTE, respuesta.getTipoNegocio());
        verify(usuarioRepository, times(1)).save(comprador);
    }

    @Test
    @DisplayName("Debe rechazar dejar vacía la dirección del comprador")
    void testActualizarPerfilCompradorDireccionVacia() {
        Usuario comprador = compradorGuardado();
        simularBusqueda(comprador);
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .direccion("   ")
                .build();

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> usuarioService.actualizarPerfilComprador(comprador.getId(), request));

        assertEquals(ActualizarPerfilCompradorRequest.MENSAJE_DIRECCION_VACIA, ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar un municipio no soportado al actualizar el perfil del comprador")
    void testActualizarPerfilCompradorMunicipioInvalido() {
        Usuario comprador = compradorGuardado();
        simularBusqueda(comprador);
        ActualizarPerfilCompradorRequest request = ActualizarPerfilCompradorRequest.builder()
                .municipio("Cali")
                .build();

        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> usuarioService.actualizarPerfilComprador(comprador.getId(), request));

        assertTrue(ex.getMessage().contains("Cali"));
        verify(usuarioRepository, never()).save(any());
    }
}
