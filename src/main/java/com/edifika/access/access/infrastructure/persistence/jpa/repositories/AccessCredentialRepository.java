package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessCredentialRepository extends JpaRepository<AccessCredential, Long> {

    boolean existsByRfidUid(String rfidUid);

    Optional<AccessCredential> findByRfidUid(String rfidUid);

    List<AccessCredential> findAllByBuildingId(Long buildingId);

    List<AccessCredential> findAllByUserId(Long userId);

    @Query("SELECT c FROM AccessCredential c WHERE " +
            "(:buildingId IS NULL OR c.buildingId = :buildingId) AND " +
            "(:userId IS NULL OR c.userId = :userId) AND " +
            "(:status IS NULL OR c.status = :status)")
    List<AccessCredential> search(
            @Param("buildingId") Long buildingId,
            @Param("userId") Long userId,
            @Param("status") CredentialStatus status
    );
}
