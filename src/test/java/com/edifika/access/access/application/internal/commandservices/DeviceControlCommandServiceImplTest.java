package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.domain.exceptions.ReservationConflictException;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.commands.SetLedColorCommand;
import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceCommandStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.services.ReservationQueryService;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceControlCommandServiceImplTest {

    private DeviceCommandRepository commandRepository;
    private IotDeviceRepository deviceRepository;
    private ReservationQueryService reservationQueryService;
    private EdgeCommandPublisher edgeCommandPublisher;
    private IntegrationProperties properties;
    private DeviceControlCommandServiceImpl service;

    private IotDevice sampleDevice;

    @BeforeEach
    void setUp() {
        commandRepository = mock(DeviceCommandRepository.class);
        deviceRepository = mock(IotDeviceRepository.class);
        reservationQueryService = mock(ReservationQueryService.class);
        edgeCommandPublisher = mock(EdgeCommandPublisher.class);

        properties = new IntegrationProperties();
        properties.setDeviceCommandTtlSeconds(10);

        service = new DeviceControlCommandServiceImpl(
                commandRepository,
                deviceRepository,
                reservationQueryService,
                edgeCommandPublisher,
                properties
        );

        var issue = EdgeGateway.issue(new CreateGatewayCommand("gw-01", 12L, "Torre 1"));
        sampleDevice = new IotDevice(
                new CreateDeviceCommand("door-01", 1L, "Puerta Principal", "Norte", DeviceType.DOOR,
                        Set.of(DeviceCapability.RFID, DeviceCapability.LOCK, DeviceCapability.RGB_LED), 5),
                issue.gateway()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(sampleDevice, "id", 1L);
        sampleDevice.markOnline(Instant.now());
    }

    @Test
    void ledOnFreeAccessDoorIs409() {
        assertEquals(AccessMode.FREE_ACCESS, sampleDevice.getAccessMode());
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(sampleDevice));

        ReservationConflictException ex = assertThrows(ReservationConflictException.class, () ->
                service.issueSetLedColor(new SetLedColorCommand(1L, "#00FF88", 15L, false))
        );
        assertEquals("La puerta no está reservada", ex.getMessage());
    }

    @Test
    void nonHolderResidentIs403() {
        sampleDevice.setAccessMode(AccessMode.RESERVED, 100L);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(sampleDevice));

        DoorReservation activeRes = DoorReservation.create(new CreateReservationCommand(
                1L, 20L, Instant.now().minusSeconds(100), Instant.now().plusSeconds(1000), 20L
        ));
        when(reservationQueryService.activeReservation(eq(1L), any(Instant.class))).thenReturn(Optional.of(activeRes));

        // Resident 15 is not the holder (holder is 20)
        assertThrows(AccessDeniedException.class, () ->
                service.issueSetLedColor(new SetLedColorCommand(1L, "#00FF88", 15L, false))
        );
    }

    @Test
    void holderCanSetLedAndCreatesPendingCommand() {
        sampleDevice.setAccessMode(AccessMode.RESERVED, 100L);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(sampleDevice));

        DoorReservation activeRes = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, Instant.now().minusSeconds(100), Instant.now().plusSeconds(1000), 15L
        ));
        when(reservationQueryService.activeReservation(eq(1L), any(Instant.class))).thenReturn(Optional.of(activeRes));

        when(commandRepository.save(any(DeviceCommand.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceCommand cmd = service.issueSetLedColor(new SetLedColorCommand(1L, "#00ff88", 15L, false));

        assertNotNull(cmd);
        assertEquals(DeviceCommandStatus.PENDING, cmd.getStatus());
        assertTrue(cmd.getParams().contains("#00FF88"));
        assertEquals(15L, cmd.getRequestedBy());
        verify(commandRepository).save(cmd);
    }
}
