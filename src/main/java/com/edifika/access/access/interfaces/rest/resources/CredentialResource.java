package com.edifika.access.access.interfaces.rest.resources;

import java.time.Instant;

public record CredentialResource(
        Long id,
        String rfidUid,
        Long buildingId,
        Long userId,
        String holderName,
        String status,
        String suspensionReason,
        Instant validFrom,
        Instant validUntil,
        String origin,
        Instant createdAt,
        Instant updatedAt
) {
}
