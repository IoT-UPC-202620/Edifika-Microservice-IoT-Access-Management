package com.edifika.access.access.domain.exceptions;

public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException(Long id) {
        super("Dispositivo no encontrado con ID: " + id);
    }

    public DeviceNotFoundException(String deviceCode) {
        super("Dispositivo no encontrado con código: " + deviceCode);
    }
}
