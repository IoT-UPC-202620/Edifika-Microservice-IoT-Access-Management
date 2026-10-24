package com.edifika.access.access.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Pattern;

@Embeddable
public record RgbColor(String value) implements Serializable {

    private static final Pattern PATTERN = Pattern.compile("^#[0-9A-F]{6}$");

    public RgbColor(String value) {
        if (value == null) {
            throw new IllegalArgumentException("El color RGB no puede ser nulo");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El color RGB debe ser un hexadecimal válido de 6 caracteres con # (ej. #00FF88)");
        }
        this.value = normalized;
    }

    public static RgbColor of(String value) {
        return value != null ? new RgbColor(value) : null;
    }
}
