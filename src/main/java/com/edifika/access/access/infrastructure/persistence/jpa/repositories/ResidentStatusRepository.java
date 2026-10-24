package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.entities.ResidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResidentStatusRepository extends JpaRepository<ResidentStatus, Long> {

    Optional<ResidentStatus> findByUserId(Long userId);

    List<ResidentStatus> findAllByBuildingId(Long buildingId);
}
