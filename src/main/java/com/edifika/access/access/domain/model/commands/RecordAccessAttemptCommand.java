package com.edifika.access.access.domain.model.commands;

import java.time.Instant;

public record RecordAccessAttemptCommand(
        String attemptId,
        String gatewayCode,
        String deviceCode,
        String rfidUid,
        String residentId,
        String result,
        String reason,
        Instant occurredAt,
        String sourceEventId
) {
}
