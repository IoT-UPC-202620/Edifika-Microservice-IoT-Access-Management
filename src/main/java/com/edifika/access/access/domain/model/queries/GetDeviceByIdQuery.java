package com.edifika.access.access.domain.model.queries;

public record GetDeviceByIdQuery(Long deviceId) {
    public GetDeviceByIdQuery {
        if (deviceId == null) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
    }
}
