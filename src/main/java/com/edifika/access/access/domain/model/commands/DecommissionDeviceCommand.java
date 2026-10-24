package com.edifika.access.access.domain.model.commands;

public record DecommissionDeviceCommand(Long deviceId) {
    public DecommissionDeviceCommand {
        if (deviceId == null) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
    }
}
