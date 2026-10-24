package com.edifika.access.access.domain.model.queries;

public record GetCredentialsByUserQuery(Long userId) {
    public GetCredentialsByUserQuery {
        if (userId == null) {
            throw new IllegalArgumentException("El ID de usuario es obligatorio");
        }
    }
}
