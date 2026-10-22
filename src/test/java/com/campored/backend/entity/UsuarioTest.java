package com.campored.backend.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Usuario - Perfil de finca o negocio")
class UsuarioTest {

    @Test
    @DisplayName("No debe permitir asignar un negocio a un usuario con finca")
    void testNegocioSobreFinca() {
        Usuario usuario = new Usuario();
        usuario.asignarFinca(new Finca());

        assertThrows(IllegalStateException.class, () -> usuario.asignarNegocio(new Negocio()));
    }

    @Test
    @DisplayName("No debe permitir asignar una finca a un usuario con negocio")
    void testFincaSobreNegocio() {
        Usuario usuario = new Usuario();
        usuario.asignarNegocio(new Negocio());

        assertThrows(IllegalStateException.class, () -> usuario.asignarFinca(new Finca()));
    }

    @Test
    @DisplayName("Debe rechazar la persistencia de un usuario con finca y negocio a la vez")
    void testValidarPerfilUnico() {
        Usuario usuario = new Usuario();
        usuario.setFinca(new Finca());
        assertDoesNotThrow(usuario::validarPerfilUnico);

        usuario.setNegocio(new Negocio());
        assertThrows(IllegalStateException.class, usuario::validarPerfilUnico);
    }
}
