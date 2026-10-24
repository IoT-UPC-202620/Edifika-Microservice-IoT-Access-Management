package com.edifika.access.access.domain.exceptions;

public class DuplicateRfidUidException extends IllegalStateException {
    public DuplicateRfidUidException(String rfidUid) {
        super("Ya existe una credencial con UID: " + rfidUid);
    }
}
