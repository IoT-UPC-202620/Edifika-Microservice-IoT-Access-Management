package com.edifika.access.access.interfaces.rest.resources;

import com.edifika.access.access.domain.model.aggregates.DiscoveredDevice;

import java.time.Instant;
import java.util.List;

/**
 * Nodo identificado por un gateway. {@code pairingCode} es el código que muestra la
 * pantalla del nodo: el administrador lo compara para confirmar que es la placa correcta.
 */
public record DiscoveredDeviceResource(
        Long id,
        String deviceCode,
        Long gatewayId,
        String gatewayCode,
        Long buildingId,
        String mac,
        String firmware,
        String model,
        List<String> capabilities,
        String ip,
        Integer rssi,
        String pairingCode,
        String status,
        Long deviceId,
        Instant firstSeenAt,
        Instant lastSeenAt
) {
    public static DiscoveredDeviceResource from(DiscoveredDevice d) {
        return new DiscoveredDeviceResource(
                d.getId(),
                d.getDeviceCode(),
                d.getGatewayId(),
                d.getGatewayCode(),
                d.getBuildingId(),
                d.getMac(),
                d.getFirmware(),
                d.getModel(),
                d.getCapabilityList(),
                d.getIp(),
                d.getRssi(),
                d.getPairingCode(),
                d.getStatus().name(),
                d.getDeviceId(),
                d.getFirstSeenAt(),
                d.getLastSeenAt()
        );
    }
}
