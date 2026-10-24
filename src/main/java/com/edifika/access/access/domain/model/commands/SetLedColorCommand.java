package com.edifika.access.access.domain.model.commands;

public record SetLedColorCommand(
        Long deviceId,
        String color,
        Long actorUserId,
        boolean actorIsAdmin
) {
    public SetLedColorCommand {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
        if (color == null || color.isBlank()) {
            throw new IllegalArgumentException("El color es obligatorio");
        }
    }
}
