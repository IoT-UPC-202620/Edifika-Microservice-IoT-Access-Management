package com.edifika.access.access.domain.exceptions;

public class DuplicateGatewayCodeException extends IllegalStateException {
    public DuplicateGatewayCodeException(String gatewayCode) {
        super("Ya existe un gateway con código: " + gatewayCode);
    }
}
