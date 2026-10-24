package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.messaging.ProcessedEventGuard;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.ApplyCommandResultCommand;
import com.edifika.access.access.domain.model.commands.ApplyDeviceStatusCommand;
import com.edifika.access.access.domain.model.commands.RecordAccessAttemptCommand;
import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.model.events.PhysicalAccessAttemptRecordedEvent;
import com.edifika.access.access.domain.model.valueobjects.AccessReason;
import com.edifika.access.access.domain.model.valueobjects.AccessResult;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.access.domain.model.valueobjects.RgbColor;
import com.edifika.access.access.domain.services.EdgeEventCommandService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessAttemptRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@Transactional
public class EdgeEventCommandServiceImpl implements EdgeEventCommandService {

    private final ProcessedEventGuard processedEventGuard;
    private final AccessAttemptRepository attemptRepository;
    private final IotDeviceRepository deviceRepository;
    private final AccessCredentialRepository credentialRepository;
    private final DeviceCommandRepository commandRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EdgeEventCommandServiceImpl(
            ProcessedEventGuard processedEventGuard,
            AccessAttemptRepository attemptRepository,
            IotDeviceRepository deviceRepository,
            AccessCredentialRepository credentialRepository,
            DeviceCommandRepository commandRepository,
            ApplicationEventPublisher eventPublisher) {
        this.processedEventGuard = processedEventGuard;
        this.attemptRepository = attemptRepository;
        this.deviceRepository = deviceRepository;
        this.credentialRepository = credentialRepository;
        this.commandRepository = commandRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void recordAccessAttempt(RecordAccessAttemptCommand command) {
        if (!processedEventGuard.firstTime(command.sourceEventId())) {
            log.info("Evento de intento de acceso duplicado ignorado: {}", command.sourceEventId());
            return;
        }

        IotDevice device = deviceRepository.findByDeviceCode(command.deviceCode()).orElse(null);
        if (device == null) {
            log.warn("Dispositivo no encontrado con código: {}", command.deviceCode());
            return;
        }

        String normalizedUid = null;
        AccessCredential credential = null;
        if (command.rfidUid() != null && !command.rfidUid().isBlank()) {
            try {
                normalizedUid = new RfidUid(command.rfidUid()).value();
                credential = credentialRepository.findByRfidUid(normalizedUid).orElse(null);
            } catch (Exception ignored) {
            }
        }

        Long userId = null;
        String residentName = null;
        if (credential != null) {
            userId = credential.getUserId();
            residentName = credential.getHolderName();
        } else if (command.residentId() != null && command.residentId().matches("\\d+")) {
            userId = Long.parseLong(command.residentId());
        }

        AccessResult result = "GRANTED".equalsIgnoreCase(command.result()) ? AccessResult.GRANTED : AccessResult.DENIED;
        AccessReason reason = mapReason(command.reason(), result, credential);

        AccessAttempt attempt = new AccessAttempt(
                command.attemptId(),
                device.getId(),
                device.getDeviceCode(),
                device.getBuildingId(),
                normalizedUid != null ? normalizedUid : command.rfidUid(),
                userId,
                residentName,
                result,
                reason,
                device.getAccessMode(),
                command.occurredAt(),
                command.sourceEventId()
        );

        AccessAttempt saved = attemptRepository.save(attempt);
        eventPublisher.publishEvent(new PhysicalAccessAttemptRecordedEvent(
                saved,
                credential != null ? credential.getId() : null
        ));
    }

    @Override
    public void applyDeviceStatus(ApplyDeviceStatusCommand command) {
        if (!processedEventGuard.firstTime(command.sourceEventId())) {
            log.info("Evento de estado de dispositivo duplicado ignorado: {}", command.sourceEventId());
            return;
        }

        IotDevice device = deviceRepository.findByDeviceCode(command.deviceCode()).orElse(null);
        if (device == null) {
            log.warn("Dispositivo no encontrado con código: {}", command.deviceCode());
            return;
        }

        if (device.getLastSeenAt() != null && command.occurredAt().isBefore(device.getLastSeenAt())) {
            log.info("Evento de estado obsoleto para {}: ocurrió en {}, pero ya visto en {}",
                    device.getDeviceCode(), command.occurredAt(), device.getLastSeenAt());
            return;
        }

        String status = command.status() != null ? command.status().toLowerCase() : "";
        switch (status) {
            case "online", "active" -> device.markOnline(command.occurredAt());
            case "offline" -> device.markOffline(command.occurredAt());
            case "maintenance_started", "maintenance" -> device.startMaintenance();
            case "maintenance_ended" -> device.endMaintenance(command.occurredAt(), Duration.ofMinutes(5));
            default -> log.debug("Estado de dispositivo no transicional reportado: {}", status);
        }

        if (command.firmwareVersion() != null && !command.firmwareVersion().isBlank()) {
            device.updateFirmware(command.firmwareVersion());
        }

        deviceRepository.save(device);
    }

    @Override
    public void applyCommandResult(ApplyCommandResultCommand command) {
        if (!processedEventGuard.firstTime(command.sourceEventId())) {
            log.info("Evento de resultado de comando duplicado ignorado: {}", command.sourceEventId());
            return;
        }

        if (command.commandId() == null) {
            return;
        }

        commandRepository.findByCommandId(command.commandId()).ifPresent(cmd -> {
            Instant now = command.occurredAt() != null ? command.occurredAt() : Instant.now();
            String st = command.status() != null ? command.status().toUpperCase() : "";

            if ("OK".equals(st)) {
                cmd.markOk(now);
                deviceRepository.findById(cmd.getDeviceId()).ifPresent(d -> {
                    try {
                        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cmd.getParams());
                        if (json.has("color")) {
                            d.setLedColor(new RgbColor(json.get("color").asText()));
                            deviceRepository.save(d);
                        }
                    } catch (Exception ignored) {
                    }
                });
            } else if ("TIMEOUT".equals(st)) {
                cmd.markFailed("timeout", now);
            } else {
                cmd.markFailed(command.detail() != null ? command.detail() : "failed", now);
            }
            commandRepository.save(cmd);
        });
    }

    private AccessReason mapReason(String rawReason, AccessResult result, AccessCredential credential) {
        if (result == AccessResult.GRANTED) {
            return AccessReason.GRANTED;
        }

        if (credential != null && credential.isSuspendedForDebt()) {
            return AccessReason.DELINQUENT;
        }

        if (rawReason == null) {
            return AccessReason.UNSUPPORTED;
        }

        String norm = rawReason.trim().toLowerCase();
        return switch (norm) {
            case "ok", "granted" -> AccessReason.GRANTED;
            case "not_registered", "unknown_credential" -> AccessReason.NOT_REGISTERED;
            case "suspended", "credential_suspended" -> AccessReason.SUSPENDED;
            case "delinquent" -> AccessReason.DELINQUENT;
            case "revoked", "credential_revoked" -> AccessReason.REVOKED;
            case "expired", "credential_expired" -> AccessReason.EXPIRED;
            case "not_authorized", "reservation_required", "no_permission" -> AccessReason.NOT_AUTHORIZED;
            case "maintenance", "device_maintenance" -> AccessReason.MAINTENANCE;
            case "out_of_schedule", "outside_schedule", "schedule_outside" -> AccessReason.OUT_OF_SCHEDULE;
            default -> AccessReason.UNSUPPORTED;
        };
    }
}
