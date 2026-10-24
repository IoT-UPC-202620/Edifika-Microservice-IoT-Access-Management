package com.edifika.access.access.domain.model.commands;

public record RemoveAuthorizationCommand(
        Long reservationId,
        Long userId,
        Long actorUserId,
        boolean actorIsAdmin
) {
    public RemoveAuthorizationCommand {
        if (reservationId == null || reservationId <= 0) {
            throw new IllegalArgumentException("El ID de reserva es obligatorio");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("El ID de usuario es obligatorio");
        }
    }
}
