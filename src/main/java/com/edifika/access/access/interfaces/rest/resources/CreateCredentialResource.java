package com.edifika.access.access.interfaces.rest.resources;

import java.time.Instant;

public record CreateCredentialResource(
        String rfidUid,
        Long buildingId,
        Long userId,
        String holderName,
        Instant validFrom,
        Instant validUntil
) {
}
