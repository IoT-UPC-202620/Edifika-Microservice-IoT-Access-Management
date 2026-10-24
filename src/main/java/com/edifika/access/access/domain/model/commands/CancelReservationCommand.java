package com.edifika.access.access.domain.model.commands;

public record CancelReservationCommand(
        Long reservationId,
        Long actorUserId,
        boolean actorIsAdmin
) {
    public CancelReservationCommand {
        if (reservationId == null || reservationId <= 0) {
            throw new IllegalArgumentException("El ID de reserva es obligatorio");
        }
    }
}
