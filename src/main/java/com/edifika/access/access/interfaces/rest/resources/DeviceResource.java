package com.edifika.access.access.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

public record DeviceResource(
        Long id,
        String deviceCode,
        Long gatewayId,
        String gatewayCode,
        Long buildingId,
        String name,
        String location,
        String deviceType,
        List<String> capabilities,
        String accessMode,
        Long activeReservationId,
        String ledColor,
        int unlockSeconds,
        String firmwareVersion,
        String status,
        Instant lastSeenAt,
        Instant createdAt
) {
}
