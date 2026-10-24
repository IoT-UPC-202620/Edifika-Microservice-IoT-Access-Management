package com.edifika.access.access.domain.model.queries;

import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;

public record GetReservationsByHolderQuery(
        Long userId,
        ReservationStatus status
) {
    public GetReservationsByHolderQuery {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("El ID de usuario es obligatorio");
        }
    }
}
