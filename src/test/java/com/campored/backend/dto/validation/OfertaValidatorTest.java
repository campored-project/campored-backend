package com.campored.backend.dto.validation;

import com.campored.backend.dto.CrearOfertaRequest;
import com.campored.backend.entity.EstadoCultivo;
import com.campored.backend.entity.ModalidadLogistica;
import com.campored.backend.entity.Producto;
import com.campored.backend.entity.UnidadMedida;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OfertaValidator - Reglas de estado de cultivo y producto")
class OfertaValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciarValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    private static LocalDate hoy() {
        return LocalDate.now(OfertaValidator.ZONA_COLOMBIA);
    }

    private CrearOfertaRequest.CrearOfertaRequestBuilder requestBase() {
        return CrearOfertaRequest.builder()
                .producto(Producto.TOMATE_CHONTO)
                .cantidadDisponible(new BigDecimal("250.00"))
                .unidadMedida(UnidadMedida.KILOGRAMO)
                .precioUnitario(new BigDecimal("3500"))
                .modalidadLogistica(ModalidadLogistica.AMBAS);
    }

    private void assertUnicoError(CrearOfertaRequest request, String campo, String mensaje) {
        Set<ConstraintViolation<CrearOfertaRequest>> violaciones = validator.validate(request);
        assertEquals(1, violaciones.size());
        ConstraintViolation<CrearOfertaRequest> violacion = violaciones.iterator().next();
        assertEquals(campo, violacion.getPropertyPath().toString());
        assertEquals(mensaje, violacion.getMessage());
    }

    @Test
    @DisplayName("Debe aceptar un cultivo en crecimiento con fecha de cosecha futura")
    void testEnCrecimientoConFechaFutura() {
        CrearOfertaRequest request = requestBase()
                .estadoCultivo(EstadoCultivo.EN_CRECIMIENTO)
                .fechaEstimadaCosecha(hoy().plusDays(30))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("Debe aceptar un producto disponible sin fecha de cosecha")
    void testDisponibleSinFecha() {
        CrearOfertaRequest request = requestBase()
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("Debe aceptar el producto Otro con su descripción")
    void testProductoOtroConTexto() {
        CrearOfertaRequest request = requestBase()
                .producto(Producto.OTRO)
                .productoOtro("Lulo")
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("Debe ignorar productoOtro cuando el producto es del catálogo")
    void testProductoCatalogoIgnoraProductoOtro() {
        CrearOfertaRequest request = requestBase()
                .productoOtro("Texto que no aplica")
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("Debe rechazar un cultivo en crecimiento sin fecha de cosecha")
    void testEnCrecimientoSinFecha() {
        CrearOfertaRequest request = requestBase()
                .estadoCultivo(EstadoCultivo.EN_CRECIMIENTO)
                .build();

        assertUnicoError(request, "fechaEstimadaCosecha", CrearOfertaRequest.MENSAJE_FECHA_COSECHA_REQUERIDA);
    }

    @Test
    @DisplayName("Debe rechazar un cultivo en crecimiento con fecha de cosecha pasada")
    void testEnCrecimientoConFechaPasada() {
        CrearOfertaRequest request = requestBase()
                .estadoCultivo(EstadoCultivo.EN_CRECIMIENTO)
                .fechaEstimadaCosecha(hoy().minusDays(1))
                .build();

        assertUnicoError(request, "fechaEstimadaCosecha", CrearOfertaRequest.MENSAJE_FECHA_COSECHA_FUTURA);
    }

    @Test
    @DisplayName("Debe rechazar un cultivo en crecimiento con fecha de cosecha igual a hoy")
    void testEnCrecimientoConFechaDeHoy() {
        CrearOfertaRequest request = requestBase()
                .estadoCultivo(EstadoCultivo.EN_CRECIMIENTO)
                .fechaEstimadaCosecha(hoy())
                .build();

        assertUnicoError(request, "fechaEstimadaCosecha", CrearOfertaRequest.MENSAJE_FECHA_COSECHA_FUTURA);
    }

    @Test
    @DisplayName("Debe aceptar un producto disponible con fecha de cosecha: el servicio la descarta")
    void testDisponibleConFecha() {
        CrearOfertaRequest request = requestBase()
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .fechaEstimadaCosecha(hoy().plusDays(10))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("Debe usar los mensajes literales de los criterios BDD de US-06")
    void testMensajesBdd() {
        assertEquals("La fecha estimada de cosecha es obligatoria para productos en cultivo.",
                CrearOfertaRequest.MENSAJE_FECHA_COSECHA_REQUERIDA);
        assertEquals("Debes especificar el nombre del producto.", CrearOfertaRequest.MENSAJE_PRODUCTO_OTRO_REQUERIDO);

        Set<ConstraintViolation<CrearOfertaRequest>> violaciones = validator.validate(
                requestBase().cantidadDisponible(null).estadoCultivo(EstadoCultivo.DISPONIBLE).build());

        assertEquals(1, violaciones.size());
        assertEquals("El volumen disponible es obligatorio.", violaciones.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Debe rechazar el producto Otro sin descripción")
    void testProductoOtroSinTexto() {
        CrearOfertaRequest request = requestBase()
                .producto(Producto.OTRO)
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .build();

        assertUnicoError(request, "productoOtro", CrearOfertaRequest.MENSAJE_PRODUCTO_OTRO_REQUERIDO);
    }

    @Test
    @DisplayName("Debe rechazar el producto Otro con descripción en blanco")
    void testProductoOtroEnBlanco() {
        CrearOfertaRequest request = requestBase()
                .producto(Producto.OTRO)
                .productoOtro("   ")
                .estadoCultivo(EstadoCultivo.DISPONIBLE)
                .build();

        assertUnicoError(request, "productoOtro", CrearOfertaRequest.MENSAJE_PRODUCTO_OTRO_REQUERIDO);
    }

    @Test
    @DisplayName("Debe reportar a la vez el error de fecha y el de producto Otro")
    void testErroresCombinados() {
        CrearOfertaRequest request = requestBase()
                .producto(Producto.OTRO)
                .estadoCultivo(EstadoCultivo.EN_CRECIMIENTO)
                .build();

        Set<ConstraintViolation<CrearOfertaRequest>> violaciones = validator.validate(request);

        assertEquals(2, violaciones.size());
        assertTrue(violaciones.stream().anyMatch(v -> v.getPropertyPath().toString().equals("fechaEstimadaCosecha")));
        assertTrue(violaciones.stream().anyMatch(v -> v.getPropertyPath().toString().equals("productoOtro")));
    }

    @Test
    @DisplayName("No debe aplicar reglas condicionales cuando faltan producto y estado")
    void testCamposObligatoriosAusentes() {
        CrearOfertaRequest request = new CrearOfertaRequest();

        Set<ConstraintViolation<CrearOfertaRequest>> violaciones = validator.validate(request);

        assertTrue(violaciones.stream().noneMatch(v ->
                v.getPropertyPath().toString().equals("fechaEstimadaCosecha")
                        || v.getPropertyPath().toString().equals("productoOtro")));
        assertTrue(violaciones.stream().anyMatch(v -> v.getPropertyPath().toString().equals("estadoCultivo")));
    }
}
