package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoorReservationRepository extends JpaRepository<DoorReservation, Long> {

    List<DoorReservation> findAllByDeviceId(Long deviceId);

    List<DoorReservation> findAllByHolderUserId(Long holderUserId);

    @Query("SELECT r FROM DoorReservation r WHERE r.deviceId = :deviceId " +
            "AND r.status IN ('SCHEDULED', 'ACTIVE') " +
            "AND r.startsAt < :end AND r.endsAt > :start")
    List<DoorReservation> findOverlappingReservations(
            @Param("deviceId") Long deviceId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("SELECT r FROM DoorReservation r WHERE r.deviceId = :deviceId " +
            "AND r.status = 'ACTIVE' AND r.startsAt <= :now AND r.endsAt >= :now")
    Optional<DoorReservation> findActiveReservationForDevice(
            @Param("deviceId") Long deviceId,
            @Param("now") Instant now
    );

    List<DoorReservation> findAllByStatusAndStartsAtLessThanEqual(ReservationStatus status, Instant now);

    List<DoorReservation> findAllByStatusAndEndsAtLessThanEqual(ReservationStatus status, Instant now);

    @Query("SELECT r FROM DoorReservation r WHERE r.deviceId = :deviceId AND r.endsAt >= :now")
    List<DoorReservation> findActiveOrUpcomingForDevice(
            @Param("deviceId") Long deviceId,
            @Param("now") Instant now
    );

    @Query("SELECT r FROM DoorReservation r WHERE " +
            "(cast(:deviceId as long) IS NULL OR r.deviceId = :deviceId) AND " +
            "(cast(:status as string) IS NULL OR r.status = :status) AND " +
            "(cast(:from as java.time.Instant) IS NULL OR r.endsAt >= :from) AND " +
            "(cast(:to as java.time.Instant) IS NULL OR r.startsAt <= :to)")
    List<DoorReservation> search(
            @Param("deviceId") Long deviceId,
            @Param("status") ReservationStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
