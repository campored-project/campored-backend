package com.campored.backend.entity;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Optional;

public enum Municipio {
    ABEJORRAL("Abejorral"),
    ALEJANDRIA("Alejandría"),
    ARGELIA("Argelia"),
    COCORNA("Cocorná"),
    CONCEPCION("Concepción"),
    EL_CARMEN_DE_VIBORAL("El Carmen de Viboral"),
    EL_PENOL("El Peñol"),
    EL_RETIRO("El Retiro"),
    EL_SANTUARIO("El Santuario"),
    GRANADA("Granada"),
    GUARNE("Guarne"),
    GUATAPE("Guatapé"),
    LA_CEJA("La Ceja"),
    LA_UNION("La Unión"),
    MARINILLA("Marinilla"),
    MEDELLIN("Medellín"),
    NARINO("Nariño"),
    RIONEGRO("Rionegro"),
    SAN_CARLOS("San Carlos"),
    SAN_FRANCISCO("San Francisco"),
    SAN_LUIS("San Luis"),
    SAN_RAFAEL("San Rafael"),
    SAN_VICENTE_FERRER("San Vicente Ferrer"),
    SONSON("Sonsón");

    private final String nombre;

    Municipio(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    // Acepta "Sonsón", "sonson" o "SONSON": el frontend puede enviar el nombre visible o el código
    public static Optional<Municipio> desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String codigo = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[\\s-]+", "_");
        return Arrays.stream(values())
                .filter(municipio -> municipio.name().equals(codigo))
                .findFirst();
    }
}
