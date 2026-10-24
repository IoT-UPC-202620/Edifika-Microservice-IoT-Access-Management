package com.edifika.access.access.domain.model.events;

public record CredentialCreatedEvent(Long userId, Long credentialId, String rfidUid, Long buildingId) {
}
