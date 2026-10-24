package com.edifika.access.access.application.internal.outboundservices.messaging;

import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;

/**
 * Puerto de salida para publicación de eventos de integración en edifika.events.
 */
public interface IntegrationEventPublisher {
    void publish(String routingKey, IntegrationEnvelope envelope);
}
