package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.debts.DebtCheckService;
import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.DeviceNotFoundException;
import com.edifika.access.access.domain.exceptions.ReservationConflictException;
import com.edifika.access.access.domain.exceptions.ReservationNotFoundException;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.AddAuthorizationCommand;
import com.edifika.access.access.domain.model.commands.CancelReservationCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.commands.RemoveAuthorizationCommand;
import com.edifika.access.access.domain.model.entities.DoorAuthorization;
import com.edifika.access.access.domain.model.events.AccessModeChangedEvent;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import com.edifika.access.access.domain.services.ReservationCommandService;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@Transactional
public class ReservationCommandServiceImpl implements ReservationCommandService {

    private final DoorReservationRepository reservationRepository;
    private final IotDeviceRepository deviceRepository;
    private final DebtCheckService debtCheckService;
    private final EdgeSyncNotifier edgeSyncNotifier;
    private final ApplicationEventPublisher eventPublisher;
    private final IntegrationProperties properties;

    public ReservationCommandServiceImpl(
            DoorReservationRepository reservationRepository,
            IotDeviceRepository deviceRepository,
            DebtCheckService debtCheckService,
            EdgeSyncNotifier edgeSyncNotifier,
            ApplicationEventPublisher eventPublisher,
            IntegrationProperties properties) {
        this.reservationRepository = reservationRepository;
        this.deviceRepository = deviceRepository;
        this.debtCheckService = debtCheckService;
        this.edgeSyncNotifier = edgeSyncNotifier;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Override
    public DoorReservation createReservation(CreateReservationCommand command) {
        // 1. Lock device
        IotDevice device = deviceRepository.findWithLockById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFoundException(command.deviceId()));

        // 2. Validate device is DOOR and not DECOMMISSIONED
        if (device.getDeviceType() != DeviceType.DOOR) {
            throw new ReservationConflictException("El dispositivo no es una puerta");
        }
        if (device.getStatus() == DeviceStatus.DECOMMISSIONED) {
            throw new ReservationConflictException("El dispositivo está dado de baja");
        }

        Instant now = Instant.now();

        // 3. Validate window: startsAt not before (now - 60s)
        if (command.startsAt().isBefore(now.minusSeconds(60))) {
            throw new IllegalArgumentException("La fecha de inicio no puede estar en el pasado");
        }

        // Duration limit (max-hours)
        Duration duration = Duration.between(command.startsAt(), command.endsAt());
        int maxHours = properties.getReservationMaxHours() > 0 ? properties.getReservationMaxHours() : 12;
        if (duration.toHours() > maxHours || (duration.toHours() == maxHours && duration.toMinutesPart() > 0)) {
            throw new IllegalArgumentException("La reserva excede el límite de " + maxHours + " horas");
        }

        // 4. Validate overlap
        List<DoorReservation> overlapping = reservationRepository.findOverlappingReservations(
                command.deviceId(),
                command.startsAt(),
                command.endsAt()
        );
        if (!overlapping.isEmpty()) {
            throw new ReservationConflictException("La puerta ya tiene una reserva en ese horario");
        }

        // 5. Debt check
        if (debtCheckService.isDelinquentForReservation(command.holderUserId())) {
            throw new ReservationConflictException("El residente tiene deudas vencidas");
        }

        // 6. Create and save reservation
        DoorReservation reservation = DoorReservation.create(command);
        boolean startsImmediately = !command.startsAt().isAfter(now);
        if (startsImmediately) {
            reservation.activate();
        }

        DoorReservation saved = reservationRepository.save(reservation);

        if (startsImmediately) {
            device.setAccessMode(AccessMode.RESERVED, saved.getId());
            deviceRepository.save(device);
            eventPublisher.publishEvent(new AccessModeChangedEvent(
                    device.getId(),
                    AccessMode.FREE_ACCESS,
                    AccessMode.RESERVED,
                    saved.getId()
            ));
        }

        edgeSyncNotifier.changed(device.getGateway().getId());
        return saved;
    }

    @Override
    public void cancelReservation(CancelReservationCommand command) {
        DoorReservation reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

        boolean wasActive = reservation.getStatus() == ReservationStatus.ACTIVE;
        reservation.cancel(command.actorUserId(), command.actorIsAdmin());
        reservationRepository.save(reservation);

        IotDevice device = deviceRepository.findById(reservation.getDeviceId()).orElse(null);
        if (device != null) {
            if (wasActive) {
                device.setAccessMode(AccessMode.FREE_ACCESS, null);
                device.clearLedColor();
                deviceRepository.save(device);
                eventPublisher.publishEvent(new AccessModeChangedEvent(
                        device.getId(),
                        AccessMode.RESERVED,
                        AccessMode.FREE_ACCESS,
                        reservation.getId()
                ));
            }
            edgeSyncNotifier.changed(device.getGateway().getId());
        }
    }

    @Override
    public DoorAuthorization addAuthorization(AddAuthorizationCommand command) {
        DoorReservation reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

        DoorAuthorization auth = reservation.addAuthorization(command.userId(), command.actorUserId(), command.actorIsAdmin());
        reservationRepository.save(reservation);

        deviceRepository.findById(reservation.getDeviceId())
                .ifPresent(d -> edgeSyncNotifier.changed(d.getGateway().getId()));

        return auth;
    }

    @Override
    public void removeAuthorization(RemoveAuthorizationCommand command) {
        DoorReservation reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

        reservation.removeAuthorization(command.userId(), command.actorUserId(), command.actorIsAdmin());
        reservationRepository.save(reservation);

        deviceRepository.findById(reservation.getDeviceId())
                .ifPresent(d -> edgeSyncNotifier.changed(d.getGateway().getId()));
    }
}
