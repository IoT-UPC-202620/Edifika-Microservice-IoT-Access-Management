package com.edifika.access.access.domain.model.queries;

import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;

public record GetCredentialsQuery(Long buildingId, Long userId, CredentialStatus status) {
}
