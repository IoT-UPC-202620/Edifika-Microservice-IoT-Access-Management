package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.aggregates.DiscoveredDevice;
import com.edifika.access.access.domain.model.valueobjects.DiscoveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveredDeviceRepository extends JpaRepository<DiscoveredDevice, Long> {

    Optional<DiscoveredDevice> findByDeviceCode(String deviceCode);

    List<DiscoveredDevice> findAllByBuildingIdAndStatusOrderByLastSeenAtDesc(Long buildingId, DiscoveryStatus status);

    List<DiscoveredDevice> findAllByStatusOrderByLastSeenAtDesc(DiscoveryStatus status);
}
