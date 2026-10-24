package com.edifika.access.access.interfaces.rest.resources;

import java.time.Instant;

public record CreateReservationResource(
        Instant startsAt,
        Instant endsAt,
        Long holderUserId
) {
}
