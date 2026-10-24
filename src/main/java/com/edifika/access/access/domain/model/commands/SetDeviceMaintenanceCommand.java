package com.edifika.access.access.domain.model.commands;

public record SetDeviceMaintenanceCommand(Long deviceId, boolean enabled) {
    public SetDeviceMaintenanceCommand {
        if (deviceId == null) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
    }
}
