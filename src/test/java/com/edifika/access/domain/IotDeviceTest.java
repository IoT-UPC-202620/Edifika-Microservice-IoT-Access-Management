package com.edifika.access.domain;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IotDeviceTest {

    private EdgeGateway sampleGateway() {
        return EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A")).gateway();
    }

    private CreateDeviceCommand doorCmd(Set<DeviceCapability> capabilities) {
        return new CreateDeviceCommand(
                "esp32-acc-01",
                1L,
                "Puerta piscina",
                "Piso 1",
                DeviceType.DOOR,
                capabilities,
                5
        );
    }

    @Test
    void doorRequiresRfidAndLock() {
        EdgeGateway gw = sampleGateway();
        assertThrows(IllegalArgumentException.class, () -> new IotDevice(doorCmd(Set.of(DeviceCapability.RFID)), gw));
        assertThrows(IllegalArgumentException.class, () -> new IotDevice(doorCmd(Set.of(DeviceCapability.LOCK)), gw));
    }

    @Test
    void newDeviceIsProvisioned() {
        EdgeGateway gw = sampleGateway();
        IotDevice device = new IotDevice(doorCmd(Set.of(DeviceCapability.RFID, DeviceCapability.LOCK)), gw);
        assertEquals(DeviceStatus.PROVISIONED, device.getStatus());
        assertEquals(AccessMode.FREE_ACCESS, device.getAccessMode());
        assertTrue(device.isDoor());
        assertTrue(device.hasCapability(DeviceCapability.RFID));
        assertFalse(device.hasCapability(DeviceCapability.RGB_LED));
    }

    @Test
    void ledColorOperations() {
        EdgeGateway gw = sampleGateway();
        IotDevice device = new IotDevice(doorCmd(Set.of(DeviceCapability.RFID, DeviceCapability.LOCK, DeviceCapability.RGB_LED)), gw);
        assertNull(device.getLedColor());

        device.setLedColor(new RgbColor("#00FF88"));
        assertEquals("#00FF88", device.getLedColor());

        device.clearLedColor();
        assertNull(device.getLedColor());
    }

    @Test
    void maintenanceTransitions() {
        EdgeGateway gw = sampleGateway();
        IotDevice device = new IotDevice(doorCmd(Set.of(DeviceCapability.RFID, DeviceCapability.LOCK)), gw);
        Instant now = Instant.now();
        device.markOnline(now);
        assertEquals(DeviceStatus.ACTIVE, device.getStatus());

        device.startMaintenance();
        assertEquals(DeviceStatus.MAINTENANCE, device.getStatus());

        // End maintenance with recent heartbeat -> ACTIVE
        device.endMaintenance(now.plusSeconds(10), Duration.ofSeconds(30));
        assertEquals(DeviceStatus.ACTIVE, device.getStatus());

        // End maintenance with old heartbeat -> OFFLINE
        device.startMaintenance();
        device.endMaintenance(now.plusSeconds(60), Duration.ofSeconds(30));
        assertEquals(DeviceStatus.OFFLINE, device.getStatus());
    }
}
