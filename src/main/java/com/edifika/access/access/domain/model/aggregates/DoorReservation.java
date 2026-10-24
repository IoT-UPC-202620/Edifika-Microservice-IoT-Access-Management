package com.edifika.access.access.domain.model.aggregates;

import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.entities.DoorAuthorization;
import com.edifika.access.access.domain.model.valueobjects.AuthorizationStatus;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import com.edifika.access.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "door_reservations")
public class DoorReservation extends AuditableAbstractAggregateRoot<DoorReservation> {

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private Long holderUserId;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status;

    @Column(nullable = false)
    private Long createdBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "reservation_id")
    private List<DoorAuthorization> authorizations = new ArrayList<>();

    protected DoorReservation() {
    }

    public static DoorReservation create(CreateReservationCommand command) {
        if (command.deviceId() == null || command.deviceId() <= 0) {
            throw new IllegalArgumentException("El ID de dispositivo es obligatorio");
        }
        if (command.holderUserId() == null || command.holderUserId() <= 0) {
            throw new IllegalArgumentException("El ID de titular es obligatorio");
        }
        if (command.startsAt() == null || command.endsAt() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias");
        }
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior al inicio");
        }

        DoorReservation reservation = new DoorReservation();
        reservation.deviceId = command.deviceId();
        reservation.holderUserId = command.holderUserId();
        reservation.startsAt = command.startsAt();
        reservation.endsAt = command.endsAt();
        reservation.createdBy = command.createdBy();
        reservation.status = ReservationStatus.SCHEDULED;
        return reservation;
    }

    public DoorAuthorization addAuthorization(Long guestUserId, Long actorUserId, boolean isAdmin) {
        validateGuestManagementPermission(actorUserId, isAdmin);

        if (this.status == ReservationStatus.ENDED || this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("La reserva ya ha finalizado o fue cancelada");
        }
        if (guestUserId == null || guestUserId <= 0) {
            throw new IllegalArgumentException("El ID de usuario invitado es obligatorio");
        }
        if (guestUserId.equals(this.holderUserId)) {
            throw new IllegalArgumentException("El titular ya tiene acceso a la reserva");
        }

        for (DoorAuthorization existing : authorizations) {
            if (existing.getUserId().equals(guestUserId) && existing.isActive()) {
                throw new IllegalStateException("El usuario ya está autorizado");
            }
        }

        DoorAuthorization authorization = new DoorAuthorization(guestUserId);
        this.authorizations.add(authorization);
        return authorization;
    }

    public void removeAuthorization(Long guestUserId, Long actorUserId, boolean isAdmin) {
        validateGuestManagementPermission(actorUserId, isAdmin);

        if (this.status == ReservationStatus.ENDED || this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("La reserva ya ha finalizado o fue cancelada");
        }

        boolean found = false;
        for (DoorAuthorization existing : authorizations) {
            if (existing.getUserId().equals(guestUserId) && existing.isActive()) {
                existing.revoke();
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("Autorización no encontrada para el usuario: " + guestUserId);
        }
    }

    public void cancel(Long actorUserId, boolean isAdmin) {
        if (this.status == ReservationStatus.ENDED || this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("La reserva ya ha finalizado o fue cancelada");
        }
        if (!isAdmin && !this.holderUserId.equals(actorUserId)) {
            throw new AccessDeniedException("No tiene permisos para cancelar esta reserva");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void activate() {
        if (this.status == ReservationStatus.SCHEDULED) {
            this.status = ReservationStatus.ACTIVE;
        }
    }

    public void end() {
        if (this.status == ReservationStatus.ACTIVE || this.status == ReservationStatus.SCHEDULED) {
            this.status = ReservationStatus.ENDED;
        }
    }

    public boolean isActiveAt(Instant now) {
        return this.status == ReservationStatus.ACTIVE
                && !now.isBefore(this.startsAt)
                && !now.isAfter(this.endsAt);
    }

    public boolean isAuthorized(Long userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(this.holderUserId)) {
            return true;
        }
        return authorizations.stream()
                .anyMatch(a -> a.getUserId().equals(userId) && a.getStatus() == AuthorizationStatus.ACTIVE);
    }

    public List<Long> getActiveGuestUserIds() {
        return authorizations.stream()
                .filter(DoorAuthorization::isActive)
                .map(DoorAuthorization::getUserId)
                .toList();
    }

    private void validateGuestManagementPermission(Long actorUserId, boolean isAdmin) {
        if (!isAdmin && !this.holderUserId.equals(actorUserId)) {
            throw new AccessDeniedException("Solo el titular o un administrador pueden gestionar autorizaciones");
        }
    }
}
