package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.commands.UpdateDeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.interfaces.rest.resources.UpdateDeviceResource;

import java.util.Set;
import java.util.stream.Collectors;

public class UpdateDeviceCommandFromResourceAssembler {

    public static UpdateDeviceCommand toCommandFromResource(Long deviceId, UpdateDeviceResource resource) {
        Set<DeviceCapability> capabilities = resource.capabilities() != null
                ? resource.capabilities().stream().map(DeviceCapability::valueOf).collect(Collectors.toSet())
                : null;

        return new UpdateDeviceCommand(
                deviceId,
                resource.name(),
                resource.location(),
                capabilities,
                resource.unlockSeconds()
        );
    }
}
