package com.edifika.access.access.domain.model.queries;

import java.time.Instant;

public record GetDeviceSlotsQuery(
        Long deviceId,
        Instant from,
        Instant to
) {
    public GetDeviceSlotsQuery {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
    }
}
