package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

public record DeviceCommandResource(
        String commandId,
        Long deviceId,
        String type,
        Map<String, Object> params,
        String status,
        String detail,
        Long requestedBy,
        Instant requestedAt,
        Instant expiresAt,
        Instant resolvedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DeviceCommandResource fromEntity(DeviceCommand entity) {
        Map<String, Object> parsedParams;
        try {
            parsedParams = MAPPER.readValue(entity.getParams(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            parsedParams = Map.of();
        }

        return new DeviceCommandResource(
                entity.getCommandId(),
                entity.getDeviceId(),
                entity.getType().name(),
                parsedParams,
                entity.getStatus().name(),
                entity.getDetail(),
                entity.getRequestedBy(),
                entity.getRequestedAt(),
                entity.getExpiresAt(),
                entity.getResolvedAt()
        );
    }
}
