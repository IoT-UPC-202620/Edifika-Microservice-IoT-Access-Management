package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.interfaces.rest.resources.CreateDeviceResource;

import java.util.Set;
import java.util.stream.Collectors;

public class CreateDeviceCommandFromResourceAssembler {

    public static CreateDeviceCommand toCommandFromResource(CreateDeviceResource resource) {
        Set<DeviceCapability> capabilities = resource.capabilities() != null
                ? resource.capabilities().stream().map(DeviceCapability::valueOf).collect(Collectors.toSet())
                : Set.of();

        return new CreateDeviceCommand(
                resource.deviceCode(),
                resource.gatewayId(),
                resource.name(),
                resource.location(),
                DeviceType.valueOf(resource.deviceType()),
                capabilities,
                resource.unlockSeconds()
        );
    }
}
