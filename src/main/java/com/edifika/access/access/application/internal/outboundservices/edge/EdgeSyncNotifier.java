package com.edifika.access.access.application.internal.outboundservices.edge;

/**
 * Notificador de cambios hacia los edge gateways.
 */
public interface EdgeSyncNotifier {

    void changed(Long gatewayId);

    void changedForBuilding(Long buildingId);
}
