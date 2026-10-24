package com.edifika.access.access.domain.model.commands;

import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;

public record ChangeCredentialStatusCommand(Long credentialId, CredentialStatus status) {
    public ChangeCredentialStatusCommand {
        if (credentialId == null) {
            throw new IllegalArgumentException("El ID de credencial es obligatorio");
        }
        if (status == null) {
            throw new IllegalArgumentException("El estado de credencial es obligatorio");
        }
    }
}
