package com.edifika.access.access.domain.model.commands;

import java.time.Instant;

public record UpsertEdgeCredentialCommand(
        String uid,
        String residentRef,
        String status,
        Instant occurredAt,
        Long buildingId
) {
    public UpsertEdgeCredentialCommand {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("El UID es obligatorio");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("La fecha de ocurrencia es obligatoria");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("El ID de edificio es obligatorio");
        }
    }
}
