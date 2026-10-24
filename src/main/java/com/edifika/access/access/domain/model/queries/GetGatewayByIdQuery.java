package com.edifika.access.access.domain.model.queries;

public record GetGatewayByIdQuery(Long gatewayId) {
    public GetGatewayByIdQuery {
        if (gatewayId == null) {
            throw new IllegalArgumentException("El ID de gateway es obligatorio");
        }
    }
}
