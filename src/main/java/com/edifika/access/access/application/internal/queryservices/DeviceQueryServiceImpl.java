package com.edifika.access.access.application.internal.queryservices;

import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.queries.GetDeviceByIdQuery;
import com.edifika.access.access.domain.model.queries.GetDevicesQuery;
import com.edifika.access.access.domain.services.DeviceQueryService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DeviceQueryServiceImpl implements DeviceQueryService {

    private final IotDeviceRepository deviceRepository;

    public DeviceQueryServiceImpl(IotDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public List<IotDevice> getDevices(GetDevicesQuery query) {
        return deviceRepository.search(
                query.gatewayId(),
                query.buildingId(),
                query.deviceType(),
                query.status()
        );
    }

    @Override
    public Optional<IotDevice> getDeviceById(GetDeviceByIdQuery query) {
        return deviceRepository.findById(query.deviceId());
    }

    @Override
    public Optional<IotDevice> getDeviceByCode(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode);
    }
}
