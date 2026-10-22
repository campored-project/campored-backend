// ============================================================
// FEATURE: US-01 — Registro de Productor (Sprint 1)
// Autor: Cristian Diez
// Fecha: 2026-09-23
// Descripción: Autenticación segura con JWT y validación de datos
// ============================================================
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
import com.campored.backend.util.UsuarioMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Usuario registrarProductor(RegistroProductorRequest request) {
        String correo = validarCorreoDisponible(request.getCorreo());
        Municipio municipio = resolverMunicipio(request.getMunicipio());

        Usuario usuario = crearUsuario(correo, request.getContrasena(), request.getNombre(), Rol.PRODUCTOR);
        usuario.setTelefono(request.getTelefono());
        usuario.setWhatsapp(request.getWhatsapp());
        usuario.asignarFinca(crearFinca(request, municipio));

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Productor registrado: {}", guardado.getId());
        return guardado;
    }

    // ============================================================
    // FEATURE: US-03 — Registro de Comprador Comercial (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Registro de negocios compradores con JWT
    // ============================================================
    @Transactional
    public Usuario registrarComprador(RegistroCompradorRequest request) {
        String correo = validarCorreoDisponible(request.getCorreo());
        Municipio municipio = resolverMunicipio(request.getMunicipio());
        TipoNegocio tipoNegocio = TipoNegocio.desde(request.getTipoNegocio())
                .orElseThrow(() -> new InvalidInputException(
                        "Tipo de negocio no soportado: " + request.getTipoNegocio()));

        Usuario usuario = crearUsuario(correo, request.getContrasena(), request.getNombre(), Rol.COMPRADOR);
        usuario.setTelefono(request.getTelefono());
        usuario.setWhatsapp(request.getWhatsapp());
        usuario.asignarNegocio(crearNegocio(request, tipoNegocio, municipio));

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Comprador registrado: {} ({})", guardado.getId(), tipoNegocio);
        return guardado;
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return usuarioRepository.findByCorreo(normalizarCorreo(correo));
    }

    // ============================================================
    // FEATURE: US-04 — Gestión de Perfil del Productor (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Actualización de teléfono, WhatsApp y canales de contacto
    // ============================================================
    @Transactional
    public UsuarioResponse actualizarPerfilProductor(UUID usuarioId, ActualizarPerfilProductorRequest request) {
        Usuario productor = buscarPerfil(usuarioId, Rol.PRODUCTOR);

        siPresente(request.getTelefono(), valor -> productor.setTelefono(textoOpcional(valor)));
        siPresente(request.getWhatsapp(), valor -> productor.setWhatsapp(textoOpcional(valor)));
        siPresente(request.getCanalLlamadaHabilitado(), productor::setCanalLlamadaHabilitado);
        siPresente(request.getCanalWhatsappHabilitado(), productor::setCanalWhatsappHabilitado);

        // Se valida el estado final porque un canal puede habilitarse usando el número ya guardado
        validarCanalesContacto(productor);

        Usuario guardado = usuarioRepository.save(productor);
        log.info("Perfil de productor actualizado: {}", guardado.getId());
        return UsuarioMapper.toResponse(guardado);
    }

    // ============================================================
    // FEATURE: US-05 — Gestión de Perfil del Comprador Comercial (Sprint 1)
    // Autor: Cristian Diez
    // Fecha: 2026-09-23
    // Descripción: Actualización de dirección, municipio y datos de entrega del negocio
    // ============================================================
    @Transactional
    public UsuarioResponse actualizarPerfilComprador(UUID usuarioId, ActualizarPerfilCompradorRequest request) {
        Usuario comprador = buscarPerfil(usuarioId, Rol.COMPRADOR);
        Negocio negocio = comprador.getNegocio();

        siPresente(request.getDireccion(), valor -> negocio.setDireccion(
                textoObligatorio(valor, ActualizarPerfilCompradorRequest.MENSAJE_DIRECCION_VACIA)));
        siPresente(request.getMunicipio(), valor -> negocio.setMunicipio(resolverMunicipio(valor)));
        siPresente(request.getHorarioRecepcion(), valor -> negocio.setHorarioRecepcion(textoOpcional(valor)));
        siPresente(request.getNotasAcceso(), valor -> negocio.setNotasAcceso(textoOpcional(valor)));
        siPresente(request.getTelefono(), valor -> comprador.setTelefono(textoOpcional(valor)));
        siPresente(request.getWhatsapp(), valor -> comprador.setWhatsapp(textoOpcional(valor)));

        Usuario guardado = usuarioRepository.save(comprador);
        log.info("Perfil de comprador actualizado: {}", guardado.getId());
        return UsuarioMapper.toResponse(guardado);
    }

    private Usuario buscarPerfil(UUID usuarioId, Rol rol) {
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> usuario.getRol() == rol)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de usuario no encontrado"));
    }

    private void validarCanalesContacto(Usuario productor) {
        if (productor.isCanalLlamadaHabilitado() && estaVacio(productor.getTelefono())) {
            throw new InvalidInputException(ActualizarPerfilProductorRequest.MENSAJE_TELEFONO_REQUERIDO);
        }
        if (productor.isCanalWhatsappHabilitado() && estaVacio(productor.getWhatsapp())) {
            throw new InvalidInputException(ActualizarPerfilProductorRequest.MENSAJE_WHATSAPP_REQUERIDO);
        }
    }

    // En un PATCH, un campo ausente (null) conserva el valor actual
    private static <T> void siPresente(T valor, Consumer<T> asignar) {
        if (valor != null) {
            asignar.accept(valor);
        }
    }

    private String validarCorreoDisponible(String correoSolicitado) {
        String correo = normalizarCorreo(correoSolicitado);
        if (usuarioRepository.existsByCorreo(correo)) {
            log.warn("Intento de registro con correo duplicado: {}", correo);
            throw new ResourceAlreadyExistsException("Este correo ya está registrado");
        }
        return correo;
    }

    private Municipio resolverMunicipio(String valor) {
        return Municipio.desde(valor)
                .orElseThrow(() -> new InvalidInputException("Municipio no soportado: " + valor));
    }

    private Usuario crearUsuario(String correo, String contrasena, String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre.trim());
        usuario.setCorreo(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(contrasena));
        usuario.setRol(rol);
        return usuario;
    }

    private Finca crearFinca(RegistroProductorRequest request, Municipio municipio) {
        Finca finca = new Finca();
        finca.setNombreFinca(request.getNombreFinca().trim());
        finca.setVereda(request.getVereda().trim());
        finca.setMunicipio(municipio);
        return finca;
    }

    private Negocio crearNegocio(RegistroCompradorRequest request, TipoNegocio tipoNegocio, Municipio municipio) {
        Negocio negocio = new Negocio();
        negocio.setNombreNegocio(request.getNombreNegocio().trim());
        negocio.setTipoNegocio(tipoNegocio);
        negocio.setDireccion(request.getDireccion().trim());
        negocio.setMunicipio(municipio);
        negocio.setHorarioRecepcion(textoOpcional(request.getHorarioRecepcion()));
        negocio.setNotasAcceso(textoOpcional(request.getNotasAcceso()));
        return negocio;
    }

    private String textoOpcional(String valor) {
        return estaVacio(valor) ? null : valor.trim();
    }

    private String textoObligatorio(String valor, String mensaje) {
        if (estaVacio(valor)) {
            throw new InvalidInputException(mensaje);
        }
        return valor.trim();
    }

    private boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
