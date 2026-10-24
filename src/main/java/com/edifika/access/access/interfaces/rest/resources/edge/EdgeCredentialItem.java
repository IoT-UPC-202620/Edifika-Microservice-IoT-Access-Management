package com.edifika.access.access.interfaces.rest.resources.edge;

import java.time.Instant;

public record EdgeCredentialItem(
        String uid,
        String type,
        String residentId,
        String residentName,
        String status,
        Instant validFrom,
        Instant validUntil
) {
}
