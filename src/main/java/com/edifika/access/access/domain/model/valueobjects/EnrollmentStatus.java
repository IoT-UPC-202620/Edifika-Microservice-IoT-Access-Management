package com.edifika.access.access.domain.model.valueobjects;

/**
 * Estado de una sesión de registro de tarjeta en un lector.
 */
public enum EnrollmentStatus {
    /** El lector espera una tarjeta. */
    ACTIVE,
    /** El lector leyó una tarjeta; falta asociarla a un residente. */
    CAPTURED,
    /** El administrador la canceló o inició otra en el mismo lector. */
    CANCELLED,
    /** Venció sin que se acercara una tarjeta. */
    EXPIRED
}
