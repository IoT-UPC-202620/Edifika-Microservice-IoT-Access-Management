package com.edifika.access.access.interfaces.rest.resources;

public record DoorSummaryResource(
        Long deviceId,
        String deviceCode,
        String name,
        String location,
        Long buildingId,
        String status,
        String accessMode,
        CurrentReservationSummaryResource currentReservation,
        String ledColor,
        boolean canEnter,
        boolean canReserve,
        boolean canChangeLed
) {
}
