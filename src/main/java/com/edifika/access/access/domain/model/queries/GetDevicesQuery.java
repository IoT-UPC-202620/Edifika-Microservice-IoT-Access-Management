package com.edifika.access.access.domain.model.queries;

import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;

public record GetDevicesQuery(Long gatewayId, Long buildingId, DeviceType deviceType, DeviceStatus status) {
}
