package com.edifika.access.access.interfaces.rest.resources.edge;

import java.time.Instant;
import java.util.List;

public record EdgeReservationItem(
        String id,
        String deviceCode,
        String holderResidentId,
        Instant startsAt,
        Instant endsAt,
        List<String> authorizedResidentIds
) {
}
