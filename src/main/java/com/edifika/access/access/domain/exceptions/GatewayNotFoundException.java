package com.edifika.access.access.domain.exceptions;

public class GatewayNotFoundException extends RuntimeException {
    public GatewayNotFoundException(Long id) {
        super("Gateway no encontrado con ID: " + id);
    }

    public GatewayNotFoundException(String gatewayCode) {
        super("Gateway no encontrado con código: " + gatewayCode);
    }
}
