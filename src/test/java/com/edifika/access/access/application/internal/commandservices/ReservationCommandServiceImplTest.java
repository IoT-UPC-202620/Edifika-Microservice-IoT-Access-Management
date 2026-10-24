package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.debts.DebtCheckService;
import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.ReservationConflictException;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CancelReservationCommand;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.RgbColor;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
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

class ReservationCommandServiceImplTest {

    private DoorReservationRepository reservationRepository;
    private IotDeviceRepository deviceRepository;
    private DebtCheckService debtCheckService;
    private EdgeSyncNotifier edgeSyncNotifier;
    private ApplicationEventPublisher eventPublisher;
    private IntegrationProperties properties;
    private ReservationCommandServiceImpl service;

    private IotDevice sampleDevice;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(DoorReservationRepository.class);
        deviceRepository = mock(IotDeviceRepository.class);
        debtCheckService = mock(DebtCheckService.class);
        edgeSyncNotifier = mock(EdgeSyncNotifier.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        properties = new IntegrationProperties();
        properties.setReservationMaxHours(12);

        service = new ReservationCommandServiceImpl(
                reservationRepository,
                deviceRepository,
                debtCheckService,
                edgeSyncNotifier,
                eventPublisher,
                properties
        );

        var issue = EdgeGateway.issue(new CreateGatewayCommand("gw-01", 12L, "Torre 1"));
        sampleDevice = new IotDevice(
                new CreateDeviceCommand("door-01", 1L, "Puerta Principal", "Norte", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                issue.gateway()
        );
    }

    @Test
    void rejectsPastStartBeyondTolerance() {
        when(deviceRepository.findWithLockById(1L)).thenReturn(Optional.of(sampleDevice));

        Instant past = Instant.now().minusSeconds(120);
        Instant future = past.plusSeconds(3600);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.createReservation(new CreateReservationCommand(1L, 15L, past, future, 15L))
        );
        assertTrue(ex.getMessage().contains("pasado"));
    }

    @Test
    void rejectsLongerThanMaxHours() {
        when(deviceRepository.findWithLockById(1L)).thenReturn(Optional.of(sampleDevice));

        Instant start = Instant.now().plusSeconds(60);
        Instant end = start.plusSeconds(13 * 3600);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.createReservation(new CreateReservationCommand(1L, 15L, start, end, 15L))
        );
        assertTrue(ex.getMessage().contains("límite de 12 horas"));
    }

    @Test
    void rejectsOverlap() {
        when(deviceRepository.findWithLockById(1L)).thenReturn(Optional.of(sampleDevice));

        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(3600);

        DoorReservation existing = DoorReservation.create(new CreateReservationCommand(1L, 20L, start, end, 20L));
        when(reservationRepository.findOverlappingReservations(1L, start, end)).thenReturn(List.of(existing));

        ReservationConflictException ex = assertThrows(ReservationConflictException.class, () ->
                service.createReservation(new CreateReservationCommand(1L, 15L, start, end, 15L))
        );
        assertTrue(ex.getMessage().contains("ya tiene una reserva"));
    }

    @Test
    void rejectsDelinquentHolderWith409Message() {
        when(deviceRepository.findWithLockById(1L)).thenReturn(Optional.of(sampleDevice));

        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(3600);

        when(reservationRepository.findOverlappingReservations(1L, start, end)).thenReturn(List.of());
        when(debtCheckService.isDelinquentForReservation(15L)).thenReturn(true);

        ReservationConflictException ex = assertThrows(ReservationConflictException.class, () ->
                service.createReservation(new CreateReservationCommand(1L, 15L, start, end, 15L))
        );
        assertEquals("El residente tiene deudas vencidas", ex.getMessage());
    }

    @Test
    void cancellingActiveReservationClearsLed() {
        Instant now = Instant.now();
        DoorReservation reservation = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.minusSeconds(600), now.plusSeconds(3000), 15L
        ));
        reservation.activate();

        sampleDevice.setLedColor(new RgbColor("#00FF88"));
        assertNotNull(sampleDevice.getLedColor());

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(sampleDevice));

        service.cancelReservation(new CancelReservationCommand(5L, 15L, false));

        assertNull(sampleDevice.getLedColor());
        verify(deviceRepository).save(sampleDevice);
        verify(edgeSyncNotifier).changed(any());
    }
}
