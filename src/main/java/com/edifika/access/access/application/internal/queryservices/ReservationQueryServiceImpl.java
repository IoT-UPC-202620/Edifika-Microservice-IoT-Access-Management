package com.edifika.access.access.application.internal.queryservices;

import com.edifika.access.access.domain.exceptions.ReservationNotFoundException;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.queries.GetDeviceSlotsQuery;
import com.edifika.access.access.domain.model.queries.GetReservationsByHolderQuery;
import com.edifika.access.access.domain.model.queries.GetReservationsQuery;
import com.edifika.access.access.domain.services.ReservationQueryService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository;
import com.edifika.access.access.interfaces.rest.resources.ReservationSlotResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ReservationQueryServiceImpl implements ReservationQueryService {

    private final DoorReservationRepository reservationRepository;

    public ReservationQueryServiceImpl(DoorReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public Optional<DoorReservation> activeReservation(Long deviceId, Instant now) {
        return reservationRepository.findActiveReservationForDevice(deviceId, now);
    }

    @Override
    public boolean isAuthorized(Long deviceId, Long userId, Instant now) {
        Optional<DoorReservation> active = activeReservation(deviceId, now);
        return active.map(r -> r.isAuthorized(userId)).orElse(false);
    }

    @Override
    public List<ReservationSlotResource> getSlots(GetDeviceSlotsQuery query, Long currentUserId) {
        Instant now = Instant.now();
        Instant from = query.from() != null ? query.from() : now;
        Instant to = query.to() != null ? query.to() : now.plus(7, ChronoUnit.DAYS);

        // Max 31 days range
        if (DurationBetween(from, to) > 31 * 24 * 3600) {
            throw new IllegalArgumentException("El rango de fechas no puede superar los 31 días");
        }

        List<DoorReservation> list = reservationRepository.search(query.deviceId(), null, from, to);
        return list.stream()
                .map(r -> ReservationSlotResource.fromEntity(r, currentUserId))
                .toList();
    }

    @Override
    public List<DoorReservation> getReservationsByHolder(GetReservationsByHolderQuery query) {
        List<DoorReservation> list = reservationRepository.findAllByHolderUserId(query.userId());
        if (query.status() != null) {
            return list.stream().filter(r -> r.getStatus() == query.status()).toList();
        }
        return list;
    }

    @Override
    public List<DoorReservation> searchReservations(GetReservationsQuery query) {
        return reservationRepository.search(query.deviceId(), query.status(), query.from(), query.to());
    }

    @Override
    public DoorReservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    private static long DurationBetween(Instant start, Instant end) {
        return Math.max(0, end.getEpochSecond() - start.getEpochSecond());
    }
}
