package com.edifika.access.access.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Pattern;

@Embeddable
public record RfidUid(String value) implements Serializable {

    private static final Pattern PATTERN = Pattern.compile("^[0-9A-F]{8,20}$");

    public RfidUid(String value) {
        if (value == null) {
            throw new IllegalArgumentException("El UID RFID no puede ser nulo");
        }
        String normalized = value.replaceAll("[\\s:-]", "").toUpperCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El UID RFID debe tener entre 8 y 20 caracteres hexadecimales");
        }
        this.value = normalized;
    }

    public static RfidUid of(String value) {
        return value != null ? new RfidUid(value) : null;
    }

    public String masked() {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
