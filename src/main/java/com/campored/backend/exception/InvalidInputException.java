package com.campored.backend.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvalidInputException extends RuntimeException {

    // US-18: errores de negocio que el frontend pinta bajo cada campo, igual que los de @Valid
    private final Map<String, String> errores;

    public InvalidInputException(String message) {
        super(message);
        this.errores = Map.of();
    }

    public InvalidInputException(String message, Map<String, String> errores) {
        super(message);
        // Copia que conserva el orden de inserción: los errores salen en el orden del formulario
        this.errores = errores == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(errores));
    }

    public Map<String, String> getErrores() {
        return errores;
    }
}
