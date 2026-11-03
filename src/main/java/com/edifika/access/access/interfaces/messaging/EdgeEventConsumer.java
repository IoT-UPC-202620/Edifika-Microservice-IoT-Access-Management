package com.edifika.access.access.interfaces.messaging;

import com.edifika.access.access.domain.model.commands.ApplyCommandResultCommand;
import com.edifika.access.access.domain.model.commands.ApplyDeviceStatusCommand;
import com.edifika.access.access.domain.model.commands.RecordAccessAttemptCommand;
import com.edifika.access.access.domain.model.commands.UpsertEdgeCredentialCommand;
import com.edifika.access.access.domain.services.CredentialCommandService;
import com.edifika.access.access.domain.services.EdgeEventCommandService;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import com.edifika.access.access.application.internal.commandservices.CardEnrollmentService;
import com.edifika.access.access.application.internal.commandservices.DeviceDiscoveryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class EdgeEventConsumer {

    private final EdgeEventCommandService edgeEventCommandService;
    private final CredentialCommandService credentialCommandService;
    private final CardEnrollmentService cardEnrollmentService;
    private final DeviceDiscoveryService deviceDiscoveryService;

    public EdgeEventConsumer(
            EdgeEventCommandService edgeEventCommandService,
            CredentialCommandService credentialCommandService,
            CardEnrollmentService cardEnrollmentService,
            DeviceDiscoveryService deviceDiscoveryService) {
        this.edgeEventCommandService = edgeEventCommandService;
        this.credentialCommandService = credentialCommandService;
        this.cardEnrollmentService = cardEnrollmentService;
        this.deviceDiscoveryService = deviceDiscoveryService;
    }

    @RabbitListener(queues = "access.edge-events")
    public void onMessage(IntegrationEnvelope envelope) {
        if (envelope == null || envelope.eventType() == null) {
            log.warn("Mensaje nulo o sin eventType recibido en access.edge-events");
            return;
        }

        String eventType = envelope.eventType();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = envelope.payload() instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Instant occurredAt = envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now();

        try {
            switch (eventType) {
                case "AccessAttempt", "PhysicalAccessAttempt", "AccessAttemptRecorded" -> {
                    String attemptId = str(payload.get("attemptId"), envelope.eventId());
                    String deviceCode = str(payload.get("deviceCode"), str(payload.get("deviceId"), null));
                    String rfidUid = str(payload.get("rfidUid"), str(payload.get("credential"), str(payload.get("uid"), null)));
                    String residentId = str(payload.get("residentId"), null);
                    String result = str(payload.get("result"), "DENIED");
                    String reason = str(payload.get("reason"), null);

                    edgeEventCommandService.recordAccessAttempt(new RecordAccessAttemptCommand(
                            attemptId,
                            envelope.gatewayCode(),
                            deviceCode,
                            rfidUid,
                            residentId,
                            result,
                            reason,
                            occurredAt,
                            envelope.eventId()
                    ));
                }
                case "DeviceStatus", "DeviceStatusReported", "DeviceHeartbeatReported" -> {
                    String deviceCode = str(payload.get("deviceCode"), str(payload.get("deviceId"), null));
                    String status = str(payload.get("status"), str(payload.get("event"), null));
                    String firmwareVersion = str(payload.get("firmwareVersion"), str(payload.get("firmware"), null));

                    edgeEventCommandService.applyDeviceStatus(new ApplyDeviceStatusCommand(
                            envelope.gatewayCode(),
                            deviceCode,
                            status,
                            firmwareVersion,
                            occurredAt,
                            envelope.eventId()
                    ));
                }
                case "CredentialChanged" -> {
                    String uid = str(payload.get("uid"), str(payload.get("rfidUid"), str(payload.get("credential"), null)));
                    String residentRef = str(payload.get("residentRef"), str(payload.get("residentId"), null));
                    String status = str(payload.get("status"), "ACTIVE");
                    Long buildingId = envelope.buildingId();

                    credentialCommandService.upsertFromEdge(new UpsertEdgeCredentialCommand(
                            uid,
                            residentRef,
                            status,
                            occurredAt,
                            buildingId
                    ));
                }
                case "CommandResult", "DeviceCommandResultReported" -> {
                    String commandId = str(payload.get("commandId"), null);
                    String status = str(payload.get("status"), null);
                    String detail = str(payload.get("detail"), null);

                    edgeEventCommandService.applyCommandResult(new ApplyCommandResultCommand(
                            envelope.gatewayCode(),
                            commandId,
                            status,
                            detail,
                            occurredAt,
                            envelope.eventId()
                    ));
                }
                case "CardEnrolled" -> {
                    String deviceCode = str(payload.get("deviceCode"), str(payload.get("deviceId"), null));
                    String uid = str(payload.get("uid"), str(payload.get("credential"), null));
                    cardEnrollmentService.onCardCaptured(deviceCode, uid, occurredAt);
                }
                case "DeviceDiscovered" -> deviceDiscoveryService.onDeviceDiscovered(
                        envelope.gatewayCode(),
                        new DeviceDiscoveryService.Discovery(
                                str(payload.get("deviceCode"), str(payload.get("deviceId"), null)),
                                str(payload.get("mac"), null),
                                str(payload.get("firmware"), str(payload.get("fw"), null)),
                                str(payload.get("model"), null),
                                payload.get("capabilities") instanceof java.util.List<?> caps
                                        ? caps.stream().map(String::valueOf).toList()
                                        : java.util.List.of(),
                                str(payload.get("ip"), null),
                                payload.get("rssi") instanceof Number n ? n.intValue() : null,
                                str(payload.get("pairingCode"), null),
                                instant(payload.get("firstSeenAt")),
                                instant(payload.get("lastSeenAt"))
                        ));
                default -> log.warn("Tipo de evento no reconocido en edge consumer: {}", eventType);
            }
        } catch (Exception ex) {
            log.error("Error procesando evento {} (id: {}): {}", eventType, envelope.eventId(), ex.getMessage(), ex);
            throw ex; // Re-throw to trigger DLQ / retry policy
        }
    }

    private static Instant instant(Object val) {
        if (val == null) return null;
        try {
            return Instant.parse(val.toString());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private static String str(Object val, String defaultVal) {
        return val != null ? val.toString() : defaultVal;
    }
}
