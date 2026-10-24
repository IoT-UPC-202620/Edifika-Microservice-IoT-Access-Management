package com.edifika.access.shared.domain.model.valueobjects;

/**
 * Value object que representa un correo electrónico válido.
 * Se encarga de validar el formato antes de asignarlo.
 */
public record Email(String value) {
    public Email {
        if (value == null || !value.contains("@"))
            throw new IllegalArgumentException("Correo electrónico inválido");
    }

    public boolean validate() {
        return value != null && value.contains("@");
    }
}
