package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;

import java.time.Instant;

public record AccessAttemptResource(
        Long id,
        Long deviceId,
        String deviceCode,
        Long userId,
        Long credentialId,
        String rfidUidMasked,
        String accessMode,
        String result,
        String reason,
        Instant occurredAt,
        Instant receivedAt
) {
    public static AccessAttemptResource fromEntity(AccessAttempt entity, Long credentialId) {
        String masked = "****0000";
        if (entity.getRfidUid() != null && !entity.getRfidUid().isBlank()) {
            try {
                masked = new RfidUid(entity.getRfidUid()).masked();
            } catch (Exception ignored) {
            }
        }

        return new AccessAttemptResource(
                entity.getId(),
                entity.getDeviceId(),
                entity.getDeviceCode(),
                entity.getUserId(),
                credentialId,
                masked,
                entity.getAccessMode().name(),
                entity.getResult().name(),
                entity.getReason().name(),
                entity.getOccurredAt(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : entity.getOccurredAt()
        );
    }
}
