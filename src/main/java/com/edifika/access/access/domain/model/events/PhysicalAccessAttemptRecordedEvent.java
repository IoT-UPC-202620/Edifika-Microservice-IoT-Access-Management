package com.edifika.access.access.domain.model.events;

import com.edifika.access.access.domain.model.entities.AccessAttempt;

public record PhysicalAccessAttemptRecordedEvent(
        AccessAttempt attempt,
        Long credentialId
) {
}
