package com.edifika.access.access.domain.model.aggregates;

import com.edifika.access.access.domain.model.valueobjects.EnrollmentStatus;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

/**
 * Sesión de registro de tarjetas: un administrador pone un lector en "modo
 * registro" durante unos segundos; la primera tarjeta que se acerca se captura
 * (sin evaluarse como acceso) para luego asociarla a un residente.
 */
@Getter
@Entity
@Table(name = "card_enrollments", indexes = {
        @Index(name = "idx_card_enrollments_device", columnList = "deviceId, startedAt")
})
public class CardEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false, length = 64)
    private String deviceCode;

    @Column(nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(length = 20)
    private String capturedUid;

    @Column
    private Instant capturedAt;

    /** Comando `enroll` enviado al nodo (para seguir su confirmación). */
    @Column(length = 32)
    private String commandId;

    protected CardEnrollment() {
    }

    public CardEnrollment(Long deviceId, String deviceCode, Long buildingId, Long requestedBy, Instant now, Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("La ventana de registro debe ser positiva");
        }
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.buildingId = buildingId;
        this.requestedBy = requestedBy;
        this.status = EnrollmentStatus.ACTIVE;
        this.startedAt = now;
        this.expiresAt = now.plus(window);
    }

    public void attachCommand(String commandId) {
        this.commandId = commandId;
    }

    /** True si sigue esperando una tarjeta en el instante dado. */
    public boolean isWaiting(Instant now) {
        return status == EnrollmentStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** Marca la sesión vencida si ya pasó su ventana. Devuelve true si cambió. */
    public boolean expireIfDue(Instant now) {
        if (status == EnrollmentStatus.ACTIVE && !now.isBefore(expiresAt)) {
            status = EnrollmentStatus.EXPIRED;
            return true;
        }
        return false;
    }

    /**
     * Registra la tarjeta leída. Se acepta una lectura llegada hasta unos
     * segundos después del vencimiento si el nodo la leyó dentro de la ventana
     * (el evento viaja por el edge y puede demorar).
     */
    public void capture(String rawUid, Instant readAt) {
        if (status != EnrollmentStatus.ACTIVE) {
            throw new IllegalStateException("La sesión de registro ya no está activa");
        }
        if (readAt.isAfter(expiresAt)) {
            throw new IllegalStateException("La tarjeta se leyó después de que venció la sesión de registro");
        }
        this.capturedUid = new RfidUid(rawUid).value();
        this.capturedAt = readAt;
        this.status = EnrollmentStatus.CAPTURED;
    }

    public void cancel() {
        if (status == EnrollmentStatus.ACTIVE) {
            status = EnrollmentStatus.CANCELLED;
        }
    }
}
