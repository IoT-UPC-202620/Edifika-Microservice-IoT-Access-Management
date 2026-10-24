package com.edifika.access.access.interfaces.rest.resources;

import java.time.Instant;

public record GatewayResource(
        Long id,
        String gatewayCode,
        Long buildingId,
        String name,
        String status,
        long syncVersion,
        Instant lastSyncAt,
        Instant createdAt
) {
}
