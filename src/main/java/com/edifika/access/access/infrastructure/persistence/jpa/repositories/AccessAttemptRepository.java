package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.valueobjects.AccessResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccessAttemptRepository extends JpaRepository<AccessAttempt, Long> {

    Optional<AccessAttempt> findBySourceEventId(String sourceEventId);

    List<AccessAttempt> findAllByDeviceIdOrderByOccurredAtDesc(Long deviceId, Pageable pageable);

    List<AccessAttempt> findAllByUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);

    @Query("SELECT a FROM AccessAttempt a WHERE " +
            "(:deviceId IS NULL OR a.deviceId = :deviceId) AND " +
            "(:userId IS NULL OR a.userId = :userId) AND " +
            "(:result IS NULL OR a.result = :result) AND " +
            "(:from IS NULL OR a.occurredAt >= :from) AND " +
            "(:to IS NULL OR a.occurredAt <= :to) " +
            "ORDER BY a.occurredAt DESC")
    List<AccessAttempt> search(
            @Param("deviceId") Long deviceId,
            @Param("userId") Long userId,
            @Param("result") AccessResult result,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
