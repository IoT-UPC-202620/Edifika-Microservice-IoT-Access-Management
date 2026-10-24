package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.interfaces.rest.resources.DeviceResource;

import java.util.List;

public class DeviceResourceFromEntityAssembler {

    public static DeviceResource toResourceFromEntity(IotDevice entity) {
        List<String> capabilities = entity.getCapabilities().stream()
                .map(DeviceCapability::name)
                .sorted()
                .toList();

        java.time.Instant createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant() : null;

        return new DeviceResource(
                entity.getId(),
                entity.getDeviceCode(),
                entity.getGatewayId(),
                entity.getGatewayCode(),
                entity.getBuildingId(),
                entity.getName(),
                entity.getLocation(),
                entity.getDeviceType().name(),
                capabilities,
                entity.getAccessMode().name(),
                entity.getActiveReservationId(),
                entity.getLedColor(),
                entity.getUnlockSeconds(),
                entity.getFirmwareVersion(),
                entity.getStatus().name(),
                entity.getLastSeenAt(),
                createdAt
        );
    }

    public static List<DeviceResource> toResourceListFromEntities(List<IotDevice> entities) {
        return entities.stream().map(DeviceResourceFromEntityAssembler::toResourceFromEntity).toList();
    }
}
