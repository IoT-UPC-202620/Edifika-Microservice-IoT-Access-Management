package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;

import java.time.Instant;
import java.util.List;

public record DoorReservationResource(
        Long id,
        Long deviceId,
        Long holderUserId,
        Instant startsAt,
        Instant endsAt,
        String status,
        List<Long> authorizedUserIds,
        Long createdBy,
        Instant createdAt
) {
    public static DoorReservationResource fromEntity(DoorReservation entity) {
        Instant created = entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant() : Instant.now();
        return new DoorReservationResource(
                entity.getId(),
                entity.getDeviceId(),
                entity.getHolderUserId(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getStatus().name(),
                entity.getActiveGuestUserIds(),
                entity.getCreatedBy(),
                created
        );
    }
}
