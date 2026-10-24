package com.edifika.access.access.domain.model.events;

import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;

import java.util.Set;

public record DeviceUpdatedEvent(
        Long deviceId,
        String deviceCode,
        String gatewayCode,
        Long buildingId,
        String name,
        DeviceType deviceType,
        Set<DeviceCapability> capabilities,
        AccessMode accessMode,
        DeviceStatus status
) {
}
