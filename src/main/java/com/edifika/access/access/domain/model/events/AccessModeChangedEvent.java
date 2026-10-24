package com.edifika.access.access.domain.model.events;

import com.edifika.access.access.domain.model.valueobjects.AccessMode;

public record AccessModeChangedEvent(
        Long deviceId,
        AccessMode from,
        AccessMode to,
        Long reservationId
) {
}
