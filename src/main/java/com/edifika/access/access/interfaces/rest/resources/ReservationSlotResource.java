package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;

import java.time.Instant;

public record ReservationSlotResource(
        Long id,
        Instant startsAt,
        Instant endsAt,
        String status,
        boolean mine
) {
    public static ReservationSlotResource fromEntity(DoorReservation entity, Long currentUserId) {
        boolean isMine = currentUserId != null && currentUserId.equals(entity.getHolderUserId());
        return new ReservationSlotResource(
                entity.getId(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getStatus().name(),
                isMine
        );
    }
}
