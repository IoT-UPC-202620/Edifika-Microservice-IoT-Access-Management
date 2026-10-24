package com.edifika.access.access.domain.model.commands;

public record CreateGatewayCommand(String gatewayCode, Long buildingId, String name) {
    public CreateGatewayCommand {
        if (gatewayCode == null || gatewayCode.isBlank()) {
            throw new IllegalArgumentException("El código de gateway es obligatorio");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("El ID de edificio es obligatorio");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del gateway es obligatorio");
        }
    }
}
