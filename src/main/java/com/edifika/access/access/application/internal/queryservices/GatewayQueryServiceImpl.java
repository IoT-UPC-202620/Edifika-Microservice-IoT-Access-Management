package com.edifika.access.access.application.internal.queryservices;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.queries.GetAllGatewaysQuery;
import com.edifika.access.access.domain.model.queries.GetGatewayByIdQuery;
import com.edifika.access.access.domain.services.GatewayQueryService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GatewayQueryServiceImpl implements GatewayQueryService {

    private final EdgeGatewayRepository gatewayRepository;

    public GatewayQueryServiceImpl(EdgeGatewayRepository gatewayRepository) {
        this.gatewayRepository = gatewayRepository;
    }

    @Override
    public List<EdgeGateway> getAllGateways(GetAllGatewaysQuery query) {
        if (query.buildingId() != null) {
            return gatewayRepository.findAllByBuildingId(query.buildingId());
        }
        return gatewayRepository.findAll();
    }

    @Override
    public Optional<EdgeGateway> getGatewayById(GetGatewayByIdQuery query) {
        return gatewayRepository.findById(query.gatewayId());
    }

    @Override
    public Optional<EdgeGateway> getGatewayByCode(String gatewayCode) {
        return gatewayRepository.findByGatewayCode(gatewayCode);
    }
}
