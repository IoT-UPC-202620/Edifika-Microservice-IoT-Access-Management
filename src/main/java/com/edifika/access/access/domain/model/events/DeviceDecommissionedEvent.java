package com.edifika.access.access.domain.model.events;

public record DeviceDecommissionedEvent(Long deviceId, String deviceCode, Long buildingId) {
}
