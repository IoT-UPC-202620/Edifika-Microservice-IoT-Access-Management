package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.interfaces.rest.resources.CreateGatewayResource;

public class CreateGatewayCommandFromResourceAssembler {

    public static CreateGatewayCommand toCommandFromResource(CreateGatewayResource resource) {
        return new CreateGatewayCommand(
                resource.gatewayCode(),
                resource.buildingId(),
                resource.name()
        );
    }
}
