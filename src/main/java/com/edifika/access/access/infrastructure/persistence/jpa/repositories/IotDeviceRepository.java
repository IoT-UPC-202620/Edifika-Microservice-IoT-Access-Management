package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IotDeviceRepository extends JpaRepository<IotDevice, Long> {

    boolean existsByDeviceCode(String deviceCode);

    Optional<IotDevice> findByDeviceCode(String deviceCode);

    List<IotDevice> findAllByGatewayId(Long gatewayId);

    List<IotDevice> findAllByGatewayBuildingId(Long buildingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM IotDevice d WHERE d.id = :id")
    Optional<IotDevice> findWithLockById(@Param("id") Long id);

    @Query("SELECT d FROM IotDevice d JOIN d.gateway g WHERE " +
            "(:gatewayId IS NULL OR g.id = :gatewayId) AND " +
            "(:buildingId IS NULL OR g.buildingId = :buildingId) AND " +
            "(:deviceType IS NULL OR d.deviceType = :deviceType) AND " +
            "(:status IS NULL OR d.status = :status)")
    List<IotDevice> search(
            @Param("gatewayId") Long gatewayId,
            @Param("buildingId") Long buildingId,
            @Param("deviceType") DeviceType deviceType,
            @Param("status") DeviceStatus status
    );
}
