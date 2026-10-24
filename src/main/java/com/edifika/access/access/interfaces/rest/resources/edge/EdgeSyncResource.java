package com.edifika.access.access.interfaces.rest.resources.edge;

import java.util.List;

public record EdgeSyncResource(
        long version,
        boolean full,
        List<EdgeCredentialItem> credentials,
        List<EdgeDeviceItem> devices,
        List<String> removedDeviceCodes,
        List<EdgeReservationItem> reservations,
        List<String> removedReservationIds,
        List<Object> permissions,
        List<Object> areaSchedules
) {
    public static EdgeSyncResource emptyIncrement(long version) {
        return new EdgeSyncResource(
                version,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
