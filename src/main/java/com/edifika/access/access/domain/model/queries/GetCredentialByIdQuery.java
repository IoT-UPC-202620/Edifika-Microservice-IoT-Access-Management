package com.edifika.access.access.domain.model.queries;

public record GetCredentialByIdQuery(Long credentialId) {
    public GetCredentialByIdQuery {
        if (credentialId == null) {
            throw new IllegalArgumentException("El ID de credencial es obligatorio");
        }
    }
}
