package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.queries.GetDeviceSlotsQuery;
import com.edifika.access.access.domain.model.queries.GetReservationsByHolderQuery;
import com.edifika.access.access.domain.model.queries.GetReservationsQuery;
import com.edifika.access.access.interfaces.rest.resources.ReservationSlotResource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationQueryService {

    Optional<DoorReservation> activeReservation(Long deviceId, Instant now);

    boolean isAuthorized(Long deviceId, Long userId, Instant now);

    List<ReservationSlotResource> getSlots(GetDeviceSlotsQuery query, Long currentUserId);

    List<DoorReservation> getReservationsByHolder(GetReservationsByHolderQuery query);

    List<DoorReservation> searchReservations(GetReservationsQuery query);

    DoorReservation getReservationById(Long id);
}
