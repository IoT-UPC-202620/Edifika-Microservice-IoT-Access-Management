package com.edifika.access.access.application.internal.edge;

import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(name = "edgeSyncNotifierImpl")
public class NoOpEdgeSyncNotifier implements EdgeSyncNotifier {

    @Override
    public void changed(Long gatewayId) {
        log.debug("NoOpEdgeSyncNotifier: changed for gatewayId {}", gatewayId);
    }

    @Override
    public void changedForBuilding(Long buildingId) {
        log.debug("NoOpEdgeSyncNotifier: changed for buildingId {}", buildingId);
    }
}
