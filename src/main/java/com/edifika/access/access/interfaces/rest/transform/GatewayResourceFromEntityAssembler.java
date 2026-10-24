package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.interfaces.rest.resources.GatewayResource;

import java.time.Instant;
import java.util.List;

public class GatewayResourceFromEntityAssembler {

    public static GatewayResource toResourceFromEntity(EdgeGateway entity) {
        Instant createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant() : null;

        return new GatewayResource(
                entity.getId(),
                entity.getGatewayCode(),
                entity.getBuildingId(),
                entity.getName(),
                entity.getStatus().name(),
                entity.getSyncVersion(),
                entity.getLastSyncAt(),
                createdAt
        );
    }

    public static List<GatewayResource> toResourceListFromEntities(List<EdgeGateway> entities) {
        return entities.stream().map(GatewayResourceFromEntityAssembler::toResourceFromEntity).toList();
    }
}
