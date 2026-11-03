package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.aggregates.CardEnrollment;
import com.edifika.access.access.domain.model.valueobjects.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardEnrollmentRepository extends JpaRepository<CardEnrollment, Long> {

    /** Sesión más reciente de un lector. */
    Optional<CardEnrollment> findFirstByDeviceIdOrderByStartedAtDesc(Long deviceId);

    List<CardEnrollment> findAllByDeviceIdAndStatus(Long deviceId, EnrollmentStatus status);

    List<CardEnrollment> findAllByDeviceCodeAndStatusOrderByStartedAtDesc(String deviceCode, EnrollmentStatus status);
}
