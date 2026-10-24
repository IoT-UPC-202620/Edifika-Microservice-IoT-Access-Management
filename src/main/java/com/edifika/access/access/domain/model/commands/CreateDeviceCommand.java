package com.edifika.access.access.domain.model.commands;

import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;

import java.util.Set;

public record CreateDeviceCommand(
        String deviceCode,
        Long gatewayId,
        String name,
        String location,
        DeviceType deviceType,
        Set<DeviceCapability> capabilities,
        int unlockSeconds
) {
    public CreateDeviceCommand {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("El código de dispositivo es obligatorio");
        }
        if (gatewayId == null) {
            throw new IllegalArgumentException("El ID de gateway es obligatorio");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del dispositivo es obligatorio");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("La ubicación es obligatoria");
        }
        if (deviceType == null) {
            throw new IllegalArgumentException("El tipo de dispositivo es obligatorio");
        }
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("Las capacidades no pueden ser vacías");
        }
        if (unlockSeconds <= 0) {
            throw new IllegalArgumentException("El tiempo de apertura debe ser mayor a 0");
        }
    }
}
