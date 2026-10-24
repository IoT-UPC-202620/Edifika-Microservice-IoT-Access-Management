package com.edifika.access.access.interfaces.rest.resources;

import java.util.List;

public record CreateDeviceResource(
        String deviceCode,
        Long gatewayId,
        String name,
        String location,
        String deviceType,
        List<String> capabilities,
        int unlockSeconds
) {
}
