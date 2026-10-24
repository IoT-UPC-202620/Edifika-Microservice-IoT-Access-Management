package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EdgeGatewayRepository extends JpaRepository<EdgeGateway, Long> {

    boolean existsByGatewayCode(String gatewayCode);

    Optional<EdgeGateway> findByGatewayCode(String gatewayCode);

    List<EdgeGateway> findAllByBuildingId(Long buildingId);
}
