package com.edifika.access.access.application.internal.eventhandlers;

import com.edifika.access.access.application.internal.outboundservices.messaging.IntegrationEventPublisher;
import com.edifika.access.access.domain.model.events.DeviceDecommissionedEvent;
import com.edifika.access.access.domain.model.events.DeviceRegisteredEvent;
import com.edifika.access.access.domain.model.events.DeviceUpdatedEvent;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DeviceRegistryIntegrationEventHandler {

    private final IntegrationEventPublisher publisher;

    public DeviceRegistryIntegrationEventHandler(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DeviceRegisteredEvent event) {
        log.info("Publicando evento de integración DeviceRegistered para dispositivo {}", event.deviceCode());
        Map<String, Object> payload = Map.of(
                "deviceId", event.deviceId(),
                "deviceCode", event.deviceCode(),
                "gatewayCode", event.gatewayCode(),
                "buildingId", event.buildingId(),
                "name", event.name(),
                "deviceType", event.deviceType().name(),
                "capabilities", event.capabilities().stream().map(Enum::name).sorted().toList(),
                "accessMode", event.accessMode().name(),
                "status", event.status().name()
        );

        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "DeviceRegistered",
                event.buildingId(),
                event.gatewayCode(),
                null,
                payload
        );
        publisher.publish("iot.device-registered", envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DeviceUpdatedEvent event) {
        log.info("Publicando evento de integración DeviceUpdated para dispositivo {}", event.deviceCode());
        Map<String, Object> payload = Map.of(
                "deviceId", event.deviceId(),
                "deviceCode", event.deviceCode(),
                "gatewayCode", event.gatewayCode(),
                "buildingId", event.buildingId(),
                "name", event.name(),
                "deviceType", event.deviceType().name(),
                "capabilities", event.capabilities().stream().map(Enum::name).sorted().toList(),
                "accessMode", event.accessMode().name(),
                "status", event.status().name()
        );

        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "DeviceUpdated",
                event.buildingId(),
                event.gatewayCode(),
                null,
                payload
        );
        publisher.publish("iot.device-updated", envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DeviceDecommissionedEvent event) {
        log.info("Publicando evento de integración DeviceDecommissioned para dispositivo {}", event.deviceCode());
        Map<String, Object> payload = Map.of(
                "deviceId", event.deviceId(),
                "deviceCode", event.deviceCode()
        );

        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "DeviceDecommissioned",
                event.buildingId(),
                null,
                null,
                payload
        );
        publisher.publish("iot.device-decommissioned", envelope);
    }
}
