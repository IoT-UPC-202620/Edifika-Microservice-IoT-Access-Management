package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.commands.CreateCredentialCommand;
import com.edifika.access.access.interfaces.rest.resources.CreateCredentialResource;

public class CreateCredentialCommandFromResourceAssembler {

    public static CreateCredentialCommand toCommandFromResource(CreateCredentialResource resource) {
        return new CreateCredentialCommand(
                resource.rfidUid(),
                resource.buildingId(),
                resource.userId(),
                resource.holderName(),
                resource.validFrom(),
                resource.validUntil()
        );
    }
}
