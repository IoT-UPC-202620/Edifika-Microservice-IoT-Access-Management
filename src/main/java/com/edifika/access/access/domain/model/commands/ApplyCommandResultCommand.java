package com.edifika.access.access.domain.model.commands;

import java.time.Instant;

public record ApplyCommandResultCommand(
        String gatewayCode,
        String commandId,
        String status,
        String detail,
        Instant occurredAt,
        String sourceEventId
) {
}
