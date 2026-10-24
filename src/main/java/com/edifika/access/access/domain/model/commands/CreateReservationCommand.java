package com.edifika.access.access.domain.model.commands;

import java.time.Instant;

public record CreateReservationCommand(
        Long deviceId,
        Long holderUserId,
        Instant startsAt,
        Instant endsAt,
        Long createdBy
) {
    public CreateReservationCommand {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
        if (holderUserId == null || holderUserId <= 0) {
            throw new IllegalArgumentException("El ID de titular es obligatorio");
        }
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior al inicio");
        }
    }
}
