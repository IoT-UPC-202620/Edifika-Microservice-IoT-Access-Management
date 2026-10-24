package com.edifika.access.access.domain.model.queries;

import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;

import java.time.Instant;

public record GetReservationsQuery(
        Long deviceId,
        ReservationStatus status,
        Instant from,
        Instant to
) {
}
