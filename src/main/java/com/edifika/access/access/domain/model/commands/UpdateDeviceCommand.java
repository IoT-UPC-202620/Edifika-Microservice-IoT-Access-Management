package com.edifika.access.access.domain.model.commands;

import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;

import java.util.Set;

public record UpdateDeviceCommand(
        Long deviceId,
        String name,
        String location,
        Set<DeviceCapability> capabilities,
        Integer unlockSeconds
) {
    public UpdateDeviceCommand {
        if (deviceId == null) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
        if (unlockSeconds != null && unlockSeconds <= 0) {
            throw new IllegalArgumentException("El tiempo de apertura debe ser mayor a 0");
        }
    }
}
