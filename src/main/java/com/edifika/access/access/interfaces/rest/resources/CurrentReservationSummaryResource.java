package com.edifika.access.access.interfaces.rest.resources;

import java.time.Instant;

public record CurrentReservationSummaryResource(
        Long id,
        Instant startsAt,
        Instant endsAt,
        boolean mine
) {
}
