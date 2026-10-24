package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.domain.exceptions.DuplicateGatewayCodeException;
import com.edifika.access.access.domain.exceptions.GatewayNotFoundException;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.RotateGatewayApiKeyCommand;
import com.edifika.access.access.domain.services.GatewayCommandService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GatewayCommandServiceImpl implements GatewayCommandService {

    private final EdgeGatewayRepository gatewayRepository;

    public GatewayCommandServiceImpl(EdgeGatewayRepository gatewayRepository) {
        this.gatewayRepository = gatewayRepository;
    }

    @Override
    public EdgeGateway.ApiKeyIssue createGateway(CreateGatewayCommand command) {
        if (gatewayRepository.existsByGatewayCode(command.gatewayCode())) {
            throw new DuplicateGatewayCodeException(command.gatewayCode());
        }
        var issue = EdgeGateway.issue(command);
        EdgeGateway saved = gatewayRepository.save(issue.gateway());
        return new EdgeGateway.ApiKeyIssue(saved, issue.plainApiKey());
    }

    @Override
    public EdgeGateway.ApiKeyIssue rotateApiKey(RotateGatewayApiKeyCommand command) {
        EdgeGateway gateway = gatewayRepository.findById(command.gatewayId())
                .orElseThrow(() -> new GatewayNotFoundException(command.gatewayId()));

        String newKey = gateway.rotateApiKey();
        EdgeGateway saved = gatewayRepository.save(gateway);
        return new EdgeGateway.ApiKeyIssue(saved, newKey);
    }
}
