package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.domain.model.entities.DoorAuthorization;

import java.time.Instant;

public record AuthorizationResource(
        Long reservationId,
        Long userId,
        String status,
        Instant grantedAt
) {
    public static AuthorizationResource fromEntity(Long reservationId, DoorAuthorization authorization) {
        return new AuthorizationResource(
                reservationId,
                authorization.getUserId(),
                authorization.getStatus().name(),
                authorization.getCreatedAt()
        );
    }
}
