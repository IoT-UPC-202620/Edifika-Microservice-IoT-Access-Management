package com.edifika.access.access.interfaces.rest.resources;

import java.util.List;

public record UpdateDeviceResource(
        String name,
        String location,
        List<String> capabilities,
        Integer unlockSeconds
) {
}
