package com.edifika.access.access.application.internal.debts;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DebtCheckScheduler {

    private final DebtCheckService debtCheckService;
    private final EdgeGatewayRepository gatewayRepository;
    private final IntegrationProperties properties;

    public DebtCheckScheduler(
            DebtCheckService debtCheckService,
            EdgeGatewayRepository gatewayRepository,
            IntegrationProperties properties) {
        this.debtCheckService = debtCheckService;
        this.gatewayRepository = gatewayRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${integration.debt-check-interval-minutes:5}", timeUnit = TimeUnit.MINUTES)
    public void runScheduledDebtCheck() {
        if (!properties.isDebtCheckEnabled()) {
            return;
        }

        List<Long> buildingIds = gatewayRepository.findAll().stream()
                .map(EdgeGateway::getBuildingId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        for (Long buildingId : buildingIds) {
            try {
                debtCheckService.refreshBuilding(buildingId);
            } catch (Exception ex) {
                log.warn("Fallo en verificación periódica de deuda para edificio {}: {}", buildingId, ex.getMessage());
            }
        }
    }
}
