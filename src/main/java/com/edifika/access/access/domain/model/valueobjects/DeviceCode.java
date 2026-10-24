package com.edifika.access.access.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.regex.Pattern;

@Embeddable
public record DeviceCode(String value) implements Serializable {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9-]{3,64}$");

    public DeviceCode {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("El código de dispositivo debe cumplir con ^[a-z0-9-]{3,64}$");
        }
    }

    public static DeviceCode of(String value) {
        return new DeviceCode(value);
    }
}
