package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.access.interfaces.rest.resources.CredentialResource;

import java.time.Instant;
import java.util.List;

public class CredentialResourceFromEntityAssembler {

    public static CredentialResource toResourceFromEntity(AccessCredential entity, boolean masked) {
        Instant createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant() : null;
        Instant updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toInstant() : null;
        String uid = masked ? new RfidUid(entity.getRfidUid()).masked() : entity.getRfidUid();
        String suspensionReason = entity.getSuspensionReason() != null ? entity.getSuspensionReason().name() : null;

        return new CredentialResource(
                entity.getId(),
                uid,
                entity.getBuildingId(),
                entity.getUserId(),
                entity.getHolderName(),
                entity.getStatus().name(),
                suspensionReason,
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getOrigin().name(),
                createdAt,
                updatedAt
        );
    }

    public static CredentialResource toResourceFromEntity(AccessCredential entity) {
        return toResourceFromEntity(entity, false);
    }

    public static List<CredentialResource> toResourceListFromEntities(List<AccessCredential> entities, boolean masked) {
        return entities.stream().map(e -> toResourceFromEntity(e, masked)).toList();
    }

    public static List<CredentialResource> toResourceListFromEntities(List<AccessCredential> entities) {
        return toResourceListFromEntities(entities, false);
    }
}
