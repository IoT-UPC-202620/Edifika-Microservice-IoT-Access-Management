package com.edifika.access.access.domain.model.commands;

import java.time.Instant;

public record ApplyDeviceStatusCommand(
        String gatewayCode,
        String deviceCode,
        String status,
        String firmwareVersion,
        Instant occurredAt,
        String sourceEventId
) {
}
