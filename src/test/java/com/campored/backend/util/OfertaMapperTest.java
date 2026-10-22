// ============================================================
// FEATURE: US-09 — Marcar Oferta como Agotada (Sprint 2)
// Autor: Cristian Diez
// Fecha: 2026-10-22
// Descripción: estadoPublicacion se calcula desde activa en todas las respuestas que lo exponen
// ============================================================
package com.campored.backend.util;

import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.EstadoPublicacion;
import com.campored.backend.entity.Finca;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Municipio;
import com.campored.backend.entity.Oferta;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.Rol;
import com.campored.backend.entity.UnidadMedida;
import com.campored.backend.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("OfertaMapper - Estado de publicación calculado")
class OfertaMapperTest {

    private Oferta oferta(boolean activa) {
        Usuario productor = new Usuario();
        productor.setId(UUID.randomUUID());
        productor.setNombre("María Gómez");
        productor.setRol(Rol.PRODUCTOR);
        Finca finca = new Finca();
        finca.setId(UUID.randomUUID());
        finca.setNombreFinca("Finca El Roble");
        finca.setMunicipio(Municipio.SONSON);
        finca.setVereda("La Honda");
        productor.asignarFinca(finca);

        Oferta oferta = new Oferta();
        oferta.setId(UUID.randomUUID());
        oferta.setProductor(productor);
        oferta.setFinca(finca);
        oferta.setProducto(Producto.CAFE_PERGAMINO);
        oferta.setCantidadDisponible(new BigDecimal("40"));
        oferta.setUnidadMedida(UnidadMedida.ARROBA);
        oferta.setPrecioUnitario(new BigDecimal("52000"));
        oferta.setEstadoCultivo(EstadoCultivo.DISPONIBLE);
        oferta.setModalidadLogistica(ModalidadLogistica.AMBAS);
        oferta.setActiva(activa);
        return oferta;
    }

    @ParameterizedTest(name = "activa={0} -> {1}")
    @CsvSource({"true, ACTIVA", "false, AGOTADA"})
    @DisplayName("Debe calcular el estado de publicación desde activa")
    void testEstadoPublicacion(boolean activa, EstadoPublicacion esperado) {
        assertEquals(esperado, OfertaMapper.estadoPublicacion(oferta(activa)));
    }

    @ParameterizedTest(name = "activa={0} -> {1}")
    @CsvSource({"true, ACTIVA", "false, AGOTADA"})
    @DisplayName("Debe incluir el estado de publicación en la respuesta de mis ofertas")
    void testToResponseIncluyeEstadoPublicacion(boolean activa, EstadoPublicacion esperado) {
        assertEquals(esperado, OfertaMapper.toResponse(oferta(activa)).getEstadoPublicacion());
    }

    @Test
    @DisplayName("Debe incluir el estado de publicación en el detalle público")
    void testToDetalleResponseIncluyeEstadoPublicacion() {
        assertEquals(EstadoPublicacion.ACTIVA,
                OfertaMapper.toDetalleResponse(oferta(true), LocalDate.of(2026, 10, 22), false).estadoPublicacion());
    }
}
