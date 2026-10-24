package com.edifika.access.access.domain.model.commands;

public record RotateGatewayApiKeyCommand(Long gatewayId) {
    public RotateGatewayApiKeyCommand {
        if (gatewayId == null) {
            throw new IllegalArgumentException("El ID de gateway es obligatorio");
        }
    }
}
