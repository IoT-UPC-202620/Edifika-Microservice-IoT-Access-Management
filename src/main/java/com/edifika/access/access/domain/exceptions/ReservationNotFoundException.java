package com.edifika.access.access.domain.exceptions;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(Long id) {
        super("Reserva no encontrada con ID: " + id);
    }

    public ReservationNotFoundException(String message) {
        super(message);
    }
}
