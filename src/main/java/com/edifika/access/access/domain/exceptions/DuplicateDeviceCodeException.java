package com.edifika.access.access.domain.exceptions;

public class DuplicateDeviceCodeException extends IllegalStateException {
    public DuplicateDeviceCodeException(String deviceCode) {
        super("Ya existe un dispositivo con código: " + deviceCode);
    }
}
