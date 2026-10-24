package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.DecommissionDeviceCommand;
import com.edifika.access.access.domain.model.commands.SetDeviceMaintenanceCommand;
import com.edifika.access.access.domain.model.commands.UpdateDeviceCommand;

import java.util.Optional;

public interface DeviceCommandService {

    Optional<IotDevice> createDevice(CreateDeviceCommand command);

    Optional<IotDevice> updateDevice(UpdateDeviceCommand command);

    Optional<IotDevice> setMaintenance(SetDeviceMaintenanceCommand command);

    void decommissionDevice(DecommissionDeviceCommand command);
}
