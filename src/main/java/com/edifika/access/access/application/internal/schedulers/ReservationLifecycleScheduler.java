package com.edifika.access.access.application.internal.schedulers;

import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.events.AccessModeChangedEvent;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class ReservationLifecycleScheduler {

    private final DoorReservationRepository reservationRepository;
    private final IotDeviceRepository deviceRepository;
    private final EdgeSyncNotifier edgeSyncNotifier;
    private final ApplicationEventPublisher eventPublisher;

    public ReservationLifecycleScheduler(
            DoorReservationRepository reservationRepository,
            IotDeviceRepository deviceRepository,
            EdgeSyncNotifier edgeSyncNotifier,
            ApplicationEventPublisher eventPublisher) {
        this.reservationRepository = reservationRepository;
        this.deviceRepository = deviceRepository;
        this.edgeSyncNotifier = edgeSyncNotifier;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void runLifecycle() {
        Instant now = Instant.now();

        // 1. Activate scheduled reservations whose start time has arrived
        List<DoorReservation> toActivate = reservationRepository.findAllByStatusAndStartsAtLessThanEqual(
                ReservationStatus.SCHEDULED, now
        );

        for (DoorReservation reservation : toActivate) {
            reservation.activate();
            reservationRepository.save(reservation);

            IotDevice device = deviceRepository.findById(reservation.getDeviceId()).orElse(null);
            if (device != null) {
                device.setAccessMode(AccessMode.RESERVED, reservation.getId());
                deviceRepository.save(device);
                eventPublisher.publishEvent(new AccessModeChangedEvent(
                        device.getId(),
                        AccessMode.FREE_ACCESS,
                        AccessMode.RESERVED,
                        reservation.getId()
                ));
                edgeSyncNotifier.changed(device.getGateway().getId());
                log.info("Reserva {} activada para puerta {}", reservation.getId(), device.getDeviceCode());
            }
        }

        // 2. End active reservations whose end time has passed
        List<DoorReservation> toEnd = reservationRepository.findAllByStatusAndEndsAtLessThanEqual(
                ReservationStatus.ACTIVE, now
        );

        for (DoorReservation reservation : toEnd) {
            reservation.end();
            reservationRepository.save(reservation);

            IotDevice device = deviceRepository.findById(reservation.getDeviceId()).orElse(null);
            if (device != null) {
                device.setAccessMode(AccessMode.FREE_ACCESS, null);
                device.clearLedColor();
                deviceRepository.save(device);
                eventPublisher.publishEvent(new AccessModeChangedEvent(
                        device.getId(),
                        AccessMode.RESERVED,
                        AccessMode.FREE_ACCESS,
                        reservation.getId()
                ));
                edgeSyncNotifier.changed(device.getGateway().getId());
                log.info("Reserva {} finalizada para puerta {}", reservation.getId(), device.getDeviceCode());
            }
        }
    }
}
