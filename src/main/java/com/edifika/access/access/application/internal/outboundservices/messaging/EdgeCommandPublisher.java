package com.edifika.access.access.application.internal.outboundservices.messaging;

import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;

/**
 * Puerto de salida para publicación de órdenes hacia el gateway en edifika.edge.commands.
 */
public interface EdgeCommandPublisher {
    void publish(String gatewayCode, String command, IntegrationEnvelope envelope);
}
