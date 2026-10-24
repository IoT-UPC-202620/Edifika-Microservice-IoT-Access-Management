package com.edifika.access.access.domain.model.queries;

public record GetReservationByIdQuery(Long id) {
    public GetReservationByIdQuery {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El ID de reserva es obligatorio");
        }
    }
}
