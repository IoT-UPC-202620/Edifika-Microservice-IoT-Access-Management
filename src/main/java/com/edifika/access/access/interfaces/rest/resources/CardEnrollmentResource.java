package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.application.internal.commandservices.CardEnrollmentService.EnrollmentView;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.CardEnrollment;

import java.time.Instant;

/**
 * Sesión de registro de tarjeta. `capturedUid` va completo: este recurso solo lo
 * ve un ADMIN y lo necesita para crear la credencial.
 */
public record CardEnrollmentResource(
        Long id,
        Long deviceId,
        String deviceCode,
        Long buildingId,
        String status,
        Instant startedAt,
        Instant expiresAt,
        String capturedUid,
        Instant capturedAt,
        ExistingCredential existingCredential
) {

    /** Credencial que ya usa la tarjeta capturada (para no duplicarla). */
    public record ExistingCredential(Long id, Long userId, String holderName, String status) {
        static ExistingCredential from(AccessCredential credential) {
            if (credential == null) return null;
            return new ExistingCredential(
                    credential.getId(),
                    credential.getUserId(),
                    credential.getHolderName(),
                    credential.getStatus() != null ? credential.getStatus().name() : null
            );
        }
    }

    public static CardEnrollmentResource from(CardEnrollment enrollment, AccessCredential existing) {
        return new CardEnrollmentResource(
                enrollment.getId(),
                enrollment.getDeviceId(),
                enrollment.getDeviceCode(),
                enrollment.getBuildingId(),
                enrollment.getStatus().name(),
                enrollment.getStartedAt(),
                enrollment.getExpiresAt(),
                enrollment.getCapturedUid(),
                enrollment.getCapturedAt(),
                ExistingCredential.from(existing)
        );
    }

    public static CardEnrollmentResource from(EnrollmentView view) {
        return from(view.enrollment(), view.existingCredential());
    }
}
