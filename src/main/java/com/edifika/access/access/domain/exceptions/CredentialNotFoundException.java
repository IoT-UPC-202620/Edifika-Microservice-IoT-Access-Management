package com.edifika.access.access.domain.exceptions;

public class CredentialNotFoundException extends RuntimeException {
    public CredentialNotFoundException(Long id) {
        super("Credencial no encontrada con ID: " + id);
    }

    public CredentialNotFoundException(String rfidUid) {
        super("Credencial no encontrada con UID: " + rfidUid);
    }
}
