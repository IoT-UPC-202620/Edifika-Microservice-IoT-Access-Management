package com.edifika.access.access.application.internal.eventhandlers;

import com.edifika.access.access.application.internal.outboundservices.messaging.IntegrationEventPublisher;
import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.events.PhysicalAccessAttemptRecordedEvent;
import com.edifika.access.access.domain.model.valueobjects.AccessResult;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PhysicalAccessIntegrationEventHandler {

    private final IntegrationEventPublisher eventPublisher;

    public PhysicalAccessIntegrationEventHandler(IntegrationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessAttemptRecorded(PhysicalAccessAttemptRecordedEvent event) {
        AccessAttempt attempt = event.attempt();

        if (attempt.getResult() == AccessResult.GRANTED) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("attemptId", attempt.getId());
            payload.put("deviceId", attempt.getDeviceId());
            payload.put("deviceCode", attempt.getDeviceCode());
            payload.put("userId", attempt.getUserId());
            payload.put("credentialId", event.credentialId());
            payload.put("accessMode", attempt.getAccessMode().name());
            payload.put("occurredAt", attempt.getOccurredAt().toString());

            IntegrationEnvelope envelope = IntegrationEnvelope.of(
                    "PhysicalAccessGranted",
                    attempt.getBuildingId(),
                    null,
                    null,
                    payload
            );

            eventPublisher.publish("access.physical-access-granted", envelope);
            log.info("Publicado PhysicalAccessGranted para attempt {}", attempt.getId());
        } else {
            String maskedUid = "****0000";
            if (attempt.getRfidUid() != null && !attempt.getRfidUid().isBlank()) {
                try {
                    maskedUid = new RfidUid(attempt.getRfidUid()).masked();
                } catch (Exception ignored) {
                }
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("attemptId", attempt.getId());
            payload.put("deviceId", attempt.getDeviceId());
            payload.put("deviceCode", attempt.getDeviceCode());
            payload.put("userId", attempt.getUserId());
            payload.put("rfidUidMasked", maskedUid);
            payload.put("accessMode", attempt.getAccessMode().name());
            payload.put("reason", attempt.getReason().name());
            payload.put("occurredAt", attempt.getOccurredAt().toString());

            IntegrationEnvelope envelope = IntegrationEnvelope.of(
                    "PhysicalAccessDenied",
                    attempt.getBuildingId(),
                    null,
                    null,
                    payload
            );

            eventPublisher.publish("access.physical-access-denied", envelope);
            log.info("Publicado PhysicalAccessDenied para attempt {}", attempt.getId());
        }
    }
}
