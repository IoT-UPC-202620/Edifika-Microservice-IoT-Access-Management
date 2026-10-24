package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.queries.GetAllGatewaysQuery;
import com.edifika.access.access.domain.model.queries.GetGatewayByIdQuery;

import java.util.List;
import java.util.Optional;

public interface GatewayQueryService {

    List<EdgeGateway> getAllGateways(GetAllGatewaysQuery query);

    Optional<EdgeGateway> getGatewayById(GetGatewayByIdQuery query);

    Optional<EdgeGateway> getGatewayByCode(String gatewayCode);
}
