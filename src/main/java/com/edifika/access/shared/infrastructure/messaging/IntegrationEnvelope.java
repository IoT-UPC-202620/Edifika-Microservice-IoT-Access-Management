package com.edifika.access.shared.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre de integración AMQP de Edifika (contratos §3.2).
 */
public record IntegrationEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        Long buildingId,
        String gatewayCode,
        String correlationId,
        Object payload
) {
    public static IntegrationEnvelope of(String eventType, Long buildingId, String gatewayCode, String correlationId, Object payload) {
        return new IntegrationEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                "iot-access-management",
                buildingId,
                gatewayCode,
                correlationId,
                payload
        );
    }
}
