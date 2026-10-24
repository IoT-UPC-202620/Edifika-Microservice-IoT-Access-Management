package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.domain.model.entities.ResidentStatus;

import java.time.Instant;
import java.util.Set;

public record ResidentStatusResource(
        Long userId,
        Long buildingId,
        Set<Long> unitIds,
        boolean delinquent,
        int overdueDebtCount,
        Instant checkedAt,
        String lastError
) {
    public static ResidentStatusResource fromEntity(ResidentStatus entity) {
        return new ResidentStatusResource(
                entity.getUserId(),
                entity.getBuildingId(),
                entity.getUnitIds(),
                entity.isDelinquent(),
                entity.getOverdueDebtCount(),
                entity.getCheckedAt(),
                entity.getLastError()
        );
    }
}
