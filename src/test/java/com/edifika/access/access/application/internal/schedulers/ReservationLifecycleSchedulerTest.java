package com.edifika.access.access.application.internal.schedulers;

import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.events.AccessModeChangedEvent;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationLifecycleSchedulerTest {

    private DoorReservationRepository reservationRepository;
    private IotDeviceRepository deviceRepository;
    private EdgeSyncNotifier edgeSyncNotifier;
    private ApplicationEventPublisher eventPublisher;
    private ReservationLifecycleScheduler scheduler;

    private IotDevice sampleDevice;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(DoorReservationRepository.class);
        deviceRepository = mock(IotDeviceRepository.class);
        edgeSyncNotifier = mock(EdgeSyncNotifier.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        scheduler = new ReservationLifecycleScheduler(
                reservationRepository,
                deviceRepository,
                edgeSyncNotifier,
                eventPublisher
        );

        var issue = EdgeGateway.issue(new CreateGatewayCommand("gw-01", 12L, "Torre 1"));
        sampleDevice = new IotDevice(
                new CreateDeviceCommand("door-01", 1L, "Puerta Principal", "Norte", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                issue.gateway()
        );
    }

    @Test
    void schedulerActivatesAndEndsAndPublishesModeChanges() {
        Instant now = Instant.now();

        // 1. Scheduled reservation to activate
        DoorReservation toActivate = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.minusSeconds(10), now.plusSeconds(3600), 15L
        ));
        when(reservationRepository.findAllByStatusAndStartsAtLessThanEqual(eq(ReservationStatus.SCHEDULED), any(Instant.class)))
                .thenReturn(List.of(toActivate));

        // 2. Active reservation to end
        DoorReservation toEnd = DoorReservation.create(new CreateReservationCommand(
                1L, 16L, now.minusSeconds(7200), now.minusSeconds(10), 16L
        ));
        toEnd.activate();
        when(reservationRepository.findAllByStatusAndEndsAtLessThanEqual(eq(ReservationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(toEnd));

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(sampleDevice));

        scheduler.runLifecycle();

        assertEquals(ReservationStatus.ACTIVE, toActivate.getStatus());
        assertEquals(ReservationStatus.ENDED, toEnd.getStatus());

        verify(reservationRepository).save(toActivate);
        verify(reservationRepository).save(toEnd);
        verify(eventPublisher, times(2)).publishEvent(any(AccessModeChangedEvent.class));
    }
}
