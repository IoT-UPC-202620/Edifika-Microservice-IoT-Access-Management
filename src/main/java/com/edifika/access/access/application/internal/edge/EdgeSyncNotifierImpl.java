package com.edifika.access.access.application.internal.edge;

import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Component("edgeSyncNotifierImpl")
public class EdgeSyncNotifierImpl implements EdgeSyncNotifier {

    private final EdgeGatewayRepository gatewayRepository;
    private final EdgeCommandPublisher edgeCommandPublisher;

    public EdgeSyncNotifierImpl(EdgeGatewayRepository gatewayRepository, EdgeCommandPublisher edgeCommandPublisher) {
        this.gatewayRepository = gatewayRepository;
        this.edgeCommandPublisher = edgeCommandPublisher;
    }

    @Override
    public void changed(Long gatewayId) {
        gatewayRepository.findById(gatewayId).ifPresent(gateway -> {
            long newVersion = gateway.bumpSyncVersion();
            gatewayRepository.save(gateway);

            String gatewayCode = gateway.getGatewayCode();
            Long buildingId = gateway.getBuildingId();

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishSyncRequested(gatewayCode, buildingId, newVersion);
                    }
                });
            } else {
                publishSyncRequested(gatewayCode, buildingId, newVersion);
            }
        });
    }

    @Override
    public void changedForBuilding(Long buildingId) {
        List<EdgeGateway> gateways = gatewayRepository.findAllByBuildingId(buildingId);
        for (EdgeGateway gateway : gateways) {
            changed(gateway.getId());
        }
    }

    private void publishSyncRequested(String gatewayCode, Long buildingId, long syncVersion) {
        log.info("Publicando orden SyncRequested para gateway {} con versión {}", gatewayCode, syncVersion);
        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "SyncRequested",
                buildingId,
                gatewayCode,
                null,
                Map.of("syncVersion", syncVersion)
        );
        edgeCommandPublisher.publish(gatewayCode, "sync-requested", envelope);
    }
}
