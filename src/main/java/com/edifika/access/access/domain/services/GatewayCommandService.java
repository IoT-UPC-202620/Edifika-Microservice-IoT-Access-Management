package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.RotateGatewayApiKeyCommand;

public interface GatewayCommandService {

    EdgeGateway.ApiKeyIssue createGateway(CreateGatewayCommand command);

    EdgeGateway.ApiKeyIssue rotateApiKey(RotateGatewayApiKeyCommand command);
}
