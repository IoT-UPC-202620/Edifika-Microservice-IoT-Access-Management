package com.edifika.access.access.domain.model.commands;

import java.time.Instant;

public record CreateCredentialCommand(
        String rfidUid,
        Long buildingId,
        Long userId,
        String holderName,
        Instant validFrom,
        Instant validUntil
) {
    public CreateCredentialCommand {
        if (rfidUid == null || rfidUid.isBlank()) {
            throw new IllegalArgumentException("El UID RFID es obligatorio");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("El ID de edificio es obligatorio");
        }
        if (userId == null) {
            throw new IllegalArgumentException("El ID de usuario es obligatorio");
        }
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("El nombre del titular es obligatorio");
        }
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
    }
}
