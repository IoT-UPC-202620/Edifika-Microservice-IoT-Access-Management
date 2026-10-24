package com.edifika.access.access.application.internal.edge;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.access.interfaces.rest.resources.edge.EdgeSyncResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EdgeSnapshotServiceTest {

    private AccessCredentialRepository credentialRepository;
    private IotDeviceRepository deviceRepository;
    private com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository reservationRepository;
    private EdgeSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        credentialRepository = mock(AccessCredentialRepository.class);
        deviceRepository = mock(IotDeviceRepository.class);
        reservationRepository = mock(com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository.class);
        snapshotService = new EdgeSnapshotService(credentialRepository, deviceRepository, reservationRepository);
    }

    @Test
    void emptySnapshotWhenNoDevicesOrCredentials() {
        var issue = EdgeGateway.issue(new CreateGatewayCommand("gw-1", 10L, "Main"));
        EdgeGateway gateway = issue.gateway();

        when(credentialRepository.findAllByBuildingId(10L)).thenReturn(List.of());
        when(deviceRepository.findAllByGatewayId(gateway.getId())).thenReturn(List.of());

        EdgeSyncResource result = snapshotService.snapshot(gateway, null);

        assertNotNull(result);
        assertTrue(result.full());
        assertEquals(1L, result.version());
        assertTrue(result.credentials().isEmpty());
        assertTrue(result.devices().isEmpty());
    }

    @Test
    void snapshotIncludesValidCredentialsAndDevices() {
        var issue = EdgeGateway.issue(new CreateGatewayCommand("gw-1", 10L, "Main"));
        EdgeGateway gateway = issue.gateway();

        AccessCredential cred = AccessCredential.createCloud(
                "04A1B2C3D4E5",
                10L,
                100L,
                "Test Resident",
                Instant.now().minusSeconds(100),
                Instant.now().plusSeconds(1000)
        );

        IotDevice device = new IotDevice(
                new CreateDeviceCommand("door-1", 1L, "Puerta 1", "Lobby", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                gateway
        );

        when(credentialRepository.findAllByBuildingId(10L)).thenReturn(List.of(cred));
        when(deviceRepository.findAllByGatewayId(gateway.getId())).thenReturn(List.of(device));

        EdgeSyncResource result = snapshotService.snapshot(gateway, null);

        assertNotNull(result);
        assertEquals(1, result.credentials().size());
        assertEquals("04A1B2C3D4E5", result.credentials().get(0).uid());
        assertEquals("100", result.credentials().get(0).residentId());
        assertEquals(1, result.devices().size());
        assertEquals("door-1", result.devices().get(0).deviceCode());
    }
}
