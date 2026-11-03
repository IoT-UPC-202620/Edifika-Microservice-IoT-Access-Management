package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.domain.model.aggregates.DiscoveredDevice;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.DiscoveryStatus;
import com.edifika.access.access.domain.services.DeviceCommandService;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DiscoveredDeviceRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeviceDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
    private static final List<String> CAPS = List.of("RFID", "LOCK", "OLED");

    private DiscoveredDeviceRepository discoveredRepository;
    private EdgeGatewayRepository gatewayRepository;
    private IotDeviceRepository deviceRepository;
    private DeviceCommandService deviceCommandService;
    private EdgeCommandPublisher publisher;
    private EdgeGateway gateway;
    private DeviceDiscoveryService service;

    @BeforeEach
    void setUp() {
        discoveredRepository = mock(DiscoveredDeviceRepository.class);
        gatewayRepository = mock(EdgeGatewayRepository.class);
        deviceRepository = mock(IotDeviceRepository.class);
        deviceCommandService = mock(DeviceCommandService.class);
        publisher = mock(EdgeCommandPublisher.class);
        when(discoveredRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        gateway = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A")).gateway();
        ReflectionTestUtils.setField(gateway, "id", 1L);
        when(gatewayRepository.findByGatewayCode("edge-gateway-01")).thenReturn(Optional.of(gateway));

        IntegrationProperties properties = new IntegrationProperties();
        properties.setDeviceCommandTtlSeconds(10);
        service = new DeviceDiscoveryService(discoveredRepository, gatewayRepository, deviceRepository,
                deviceCommandService, publisher, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DeviceDiscoveryService.Discovery discovery(String firmware, Instant lastSeen) {
        return new DeviceDiscoveryService.Discovery("esp32-3c71bf", "24:6F:28:3C:71:BF", firmware, "door-node",
                CAPS, "192.168.1.40", -58, "4821", NOW.minusSeconds(60), lastSeen);
    }

    private DiscoveredDevice pending() {
        DiscoveredDevice d = new DiscoveredDevice("esp32-3c71bf", gateway, NOW.minusSeconds(60));
        d.refresh(gateway, "24:6F:28:3C:71:BF", "v0.6.0", "door-node", CAPS, "192.168.1.40", -58, "4821", NOW);
        ReflectionTestUtils.setField(d, "id", 4L);
        when(discoveredRepository.findById(4L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void aNewNodeIsStoredAsPendingInTheBuildingOfTheGatewayThatIdentifiedIt() {
        when(discoveredRepository.findByDeviceCode("esp32-3c71bf")).thenReturn(Optional.empty());

        service.onDeviceDiscovered("edge-gateway-01", discovery("v0.6.0", NOW));

        ArgumentCaptor<DiscoveredDevice> saved = ArgumentCaptor.forClass(DiscoveredDevice.class);
        verify(discoveredRepository).save(saved.capture());
        DiscoveredDevice d = saved.getValue();
        assertEquals(DiscoveryStatus.PENDING, d.getStatus());
        assertEquals(12L, d.getBuildingId());
        assertEquals(1L, d.getGatewayId());
        assertEquals("4821", d.getPairingCode());
        assertEquals(CAPS, d.getCapabilityList());
        assertEquals(NOW.minusSeconds(60), d.getFirstSeenAt());
    }

    @Test
    void aRepeatedDiscoveryUpdatesTheNodeWithoutChangingItsStatus() {
        DiscoveredDevice existing = pending();
        existing.ignore();
        when(discoveredRepository.findByDeviceCode("esp32-3c71bf")).thenReturn(Optional.of(existing));

        service.onDeviceDiscovered("edge-gateway-01", discovery("v0.6.1", NOW.plusSeconds(300)));

        assertEquals("v0.6.1", existing.getFirmware());
        assertEquals(NOW.plusSeconds(300), existing.getLastSeenAt());
        assertEquals(DiscoveryStatus.IGNORED, existing.getStatus());
    }

    @Test
    void discoveriesOfRegisteredDevicesOrUnknownGatewaysAreIgnored() {
        when(deviceRepository.existsByDeviceCode("esp32-3c71bf")).thenReturn(true);
        service.onDeviceDiscovered("edge-gateway-01", discovery("v0.6.0", NOW));
        service.onDeviceDiscovered("edge-gateway-99", discovery("v0.6.0", NOW));

        verify(discoveredRepository, never()).save(any());
    }

    @Test
    void identifySendsTheCommandThroughTheGatewayThatIdentifiedTheNode() {
        pending();

        service.identify(4L, 15, 2L);

        ArgumentCaptor<IntegrationEnvelope> envelope = ArgumentCaptor.forClass(IntegrationEnvelope.class);
        verify(publisher).publish(eq("edge-gateway-01"), eq("device-command"), envelope.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.getValue().payload();
        assertEquals("identify", payload.get("type"));
        assertEquals("esp32-3c71bf", payload.get("deviceCode"));
        assertEquals(Map.of("seconds", 15), payload.get("params"));
    }

    @Test
    void identifyRejectsOutOfRangeDurationsAndResolvedNodes() {
        DiscoveredDevice d = pending();
        assertThrows(IllegalArgumentException.class, () -> service.identify(4L, 0, 2L));
        assertThrows(IllegalArgumentException.class, () -> service.identify(4L, 61, 2L));

        d.ignore();
        assertThrows(IllegalStateException.class, () -> service.identify(4L, null, 2L));
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void adoptCreatesTheDeviceOnTheGatewayThatIdentifiedItAndMarksItAdopted() {
        DiscoveredDevice d = pending();
        IotDevice device = new IotDevice(new CreateDeviceCommand("esp32-3c71bf", 1L, "Puerta gimnasio", "Piso 2",
                DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5), gateway);
        ReflectionTestUtils.setField(device, "id", 9L);
        when(deviceCommandService.createDevice(any())).thenReturn(Optional.of(device));

        IotDevice adopted = service.adopt(4L, new DeviceDiscoveryService.Adoption("Puerta gimnasio", "Piso 2",
                DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5));

        ArgumentCaptor<CreateDeviceCommand> command = ArgumentCaptor.forClass(CreateDeviceCommand.class);
        verify(deviceCommandService).createDevice(command.capture());
        assertEquals("esp32-3c71bf", command.getValue().deviceCode());
        assertEquals(1L, command.getValue().gatewayId());
        assertEquals("Puerta gimnasio", command.getValue().name());
        assertSame(device, adopted);
        assertEquals(DiscoveryStatus.ADOPTED, d.getStatus());
        assertEquals(9L, d.getDeviceId());
    }

    @Test
    void anAdoptedNodeCannotBeAdoptedAgain() {
        DiscoveredDevice d = pending();
        d.markAdopted(9L);

        assertThrows(IllegalStateException.class, () -> service.adopt(4L, new DeviceDiscoveryService.Adoption(
                "Otra", "Piso 3", DeviceType.DOOR, Set.of(DeviceCapability.RFID), 5)));
        verify(deviceCommandService, never()).createDevice(any());
    }

    @Test
    void listDefaultsToPendingNodes() {
        service.list(12L, null);
        verify(discoveredRepository).findAllByBuildingIdAndStatusOrderByLastSeenAtDesc(12L, DiscoveryStatus.PENDING);
    }

    @Test
    void anUnknownIdIsNotFound() {
        when(discoveredRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(DeviceDiscoveryService.DiscoveredDeviceNotFoundException.class, () -> service.ignore(99L));
    }
}
