package com.edifika.access.access.interfaces.rest.resources.edge;

import java.util.List;
import java.util.Map;

public record EdgeDeviceItem(
        String deviceCode,
        String name,
        String location,
        List<String> capabilities,
        String ledColor,
        String status,
        Map<String, Object> settings
) {
}
