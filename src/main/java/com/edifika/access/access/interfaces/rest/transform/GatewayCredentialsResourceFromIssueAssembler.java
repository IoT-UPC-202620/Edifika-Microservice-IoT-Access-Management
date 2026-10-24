package com.edifika.access.access.interfaces.rest.transform;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.interfaces.rest.resources.GatewayCredentialsResource;

public class GatewayCredentialsResourceFromIssueAssembler {

    public static GatewayCredentialsResource toResourceFromIssue(EdgeGateway.ApiKeyIssue issue) {
        return new GatewayCredentialsResource(
                issue.gateway().getId(),
                issue.gateway().getGatewayCode(),
                issue.plainApiKey()
        );
    }
}
