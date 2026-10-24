package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.queries.GetDeviceByIdQuery;
import com.edifika.access.access.domain.model.queries.GetDevicesQuery;

import java.util.List;
import java.util.Optional;

public interface DeviceQueryService {

    List<IotDevice> getDevices(GetDevicesQuery query);

    Optional<IotDevice> getDeviceById(GetDeviceByIdQuery query);

    Optional<IotDevice> getDeviceByCode(String deviceCode);
}
