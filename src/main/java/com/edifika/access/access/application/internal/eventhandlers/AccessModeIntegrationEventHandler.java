package com.edifika.access.access.application.internal.eventhandlers;

import com.edifika.access.access.application.internal.outboundservices.messaging.IntegrationEventPublisher;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.events.AccessModeChangedEvent;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AccessModeIntegrationEventHandler {

    private final IntegrationEventPublisher eventPublisher;
    private final IotDeviceRepository deviceRepository;

    public AccessModeIntegrationEventHandler(
            IntegrationEventPublisher eventPublisher,
            IotDeviceRepository deviceRepository) {
        this.eventPublisher = eventPublisher;
        this.deviceRepository = deviceRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessModeChanged(AccessModeChangedEvent event) {
        IotDevice device = deviceRepository.findById(event.deviceId()).orElse(null);
        if (device == null) {
            log.warn("Dispositivo {} no encontrado para evento AccessModeChanged", event.deviceId());
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", device.getId());
        payload.put("deviceCode", device.getDeviceCode());
        payload.put("from", event.from().name());
        payload.put("to", event.to().name());
        payload.put("reservationId", event.reservationId());

        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "AccessModeChanged",
                device.getBuildingId(),
                device.getGatewayCode(),
                null,
                payload
        );

        eventPublisher.publish("iot.access-mode-changed", envelope);
        log.info("Publicado evento AccessModeChanged para dispositivo {}", device.getDeviceCode());
    }
}
