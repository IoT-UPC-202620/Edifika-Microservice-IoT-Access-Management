package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.domain.exceptions.DeviceNotFoundException;
import com.edifika.access.access.domain.exceptions.ReservationConflictException;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.CardEnrollment;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceCommandType;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.EnrollmentStatus;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.CardEnrollmentRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Modo registro de tarjetas: el administrador elige un lector, este captura la
 * siguiente tarjeta que se le acerque (sin abrir la puerta) y luego la asocia a
 * un residente creando la credencial con el endpoint de credenciales.
 */
@Slf4j
@Service
@Transactional
public class CardEnrollmentService {

    /** Tiempo que el lector espera una tarjeta. */
    public static final Duration WINDOW = Duration.ofSeconds(60);

    /** Margen para lecturas que llegan tarde por el edge (se leyeron dentro de la ventana). */
    private static final Duration LATE_EVENT_GRACE = Duration.ofSeconds(15);

    private final CardEnrollmentRepository enrollmentRepository;
    private final IotDeviceRepository deviceRepository;
    private final DeviceCommandRepository commandRepository;
    private final AccessCredentialRepository credentialRepository;
    private final EdgeCommandPublisher edgeCommandPublisher;
    private final IntegrationProperties properties;
    private final Clock clock;

    @Autowired
    public CardEnrollmentService(
            CardEnrollmentRepository enrollmentRepository,
            IotDeviceRepository deviceRepository,
            DeviceCommandRepository commandRepository,
            AccessCredentialRepository credentialRepository,
            EdgeCommandPublisher edgeCommandPublisher,
            IntegrationProperties properties) {
        this(enrollmentRepository, deviceRepository, commandRepository, credentialRepository,
                edgeCommandPublisher, properties, Clock.systemUTC());
    }

    CardEnrollmentService(
            CardEnrollmentRepository enrollmentRepository,
            IotDeviceRepository deviceRepository,
            DeviceCommandRepository commandRepository,
            AccessCredentialRepository credentialRepository,
            EdgeCommandPublisher edgeCommandPublisher,
            IntegrationProperties properties,
            Clock clock) {
        this.enrollmentRepository = enrollmentRepository;
        this.deviceRepository = deviceRepository;
        this.commandRepository = commandRepository;
        this.credentialRepository = credentialRepository;
        this.edgeCommandPublisher = edgeCommandPublisher;
        this.properties = properties;
        this.clock = clock;
    }

    /** Sesión vigente o la última del lector, con la credencial existente si la tarjeta ya está registrada. */
    public record EnrollmentView(CardEnrollment enrollment, AccessCredential existingCredential) {
    }

    /** Pone el lector en modo registro. Cancela una sesión previa del mismo lector. */
    public CardEnrollment start(Long deviceId, Long adminUserId) {
        IotDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        if (!device.hasCapability(DeviceCapability.RFID)) {
            throw new IllegalArgumentException("El dispositivo no tiene lector RFID");
        }
        if (device.getStatus() != DeviceStatus.ACTIVE) {
            throw new ReservationConflictException("El lector no está en línea");
        }
        Instant now = clock.instant();
        for (CardEnrollment previous : enrollmentRepository.findAllByDeviceIdAndStatus(deviceId, EnrollmentStatus.ACTIVE)) {
            previous.cancel();
        }

        CardEnrollment enrollment = new CardEnrollment(
                device.getId(), device.getDeviceCode(), device.getBuildingId(), adminUserId, now, WINDOW);
        String commandId = sendEnrollCommand(device, adminUserId, true, now);
        enrollment.attachCommand(commandId);
        return enrollmentRepository.save(enrollment);
    }

    /** Sale del modo registro si la sesión sigue activa. */
    public Optional<CardEnrollment> cancel(Long deviceId, Long adminUserId) {
        IotDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        Optional<CardEnrollment> latest = enrollmentRepository.findFirstByDeviceIdOrderByStartedAtDesc(deviceId);
        latest.ifPresent(enrollment -> {
            if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
                enrollment.cancel();
                sendEnrollCommand(device, adminUserId, false, clock.instant());
            }
        });
        return latest;
    }

    /** Última sesión del lector (vence la sesión si ya pasó su ventana). */
    public Optional<EnrollmentView> current(Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new DeviceNotFoundException(deviceId);
        }
        return enrollmentRepository.findFirstByDeviceIdOrderByStartedAtDesc(deviceId).map(enrollment -> {
            enrollment.expireIfDue(clock.instant());
            AccessCredential existing = enrollment.getCapturedUid() == null
                    ? null
                    : credentialRepository.findByRfidUid(enrollment.getCapturedUid()).orElse(null);
            return new EnrollmentView(enrollment, existing);
        });
    }

    /**
     * El nodo leyó una tarjeta en modo registro (evento `CardEnrolled` del edge).
     * Se asigna a la sesión activa más reciente de ese lector.
     */
    public void onCardCaptured(String deviceCode, String rawUid, Instant readAt) {
        if (deviceCode == null || rawUid == null) {
            log.warn("CardEnrolled sin deviceCode o UID; se ignora");
            return;
        }
        Instant now = clock.instant();
        Instant readTime = readAt != null && !readAt.isAfter(now) ? readAt : now;
        var waiting = enrollmentRepository.findAllByDeviceCodeAndStatusOrderByStartedAtDesc(deviceCode, EnrollmentStatus.ACTIVE);
        for (CardEnrollment enrollment : waiting) {
            boolean inWindow = !readTime.isAfter(enrollment.getExpiresAt())
                    && now.isBefore(enrollment.getExpiresAt().plus(LATE_EVENT_GRACE));
            if (inWindow) {
                enrollment.capture(RfidUid.of(rawUid).value(), readTime);
                log.info("Tarjeta {} capturada en {} (sesión {})", RfidUid.of(rawUid).masked(), deviceCode, enrollment.getId());
                return;
            }
            enrollment.expireIfDue(now);
        }
        log.warn("Tarjeta leída en modo registro en {} sin sesión activa; se ignora", deviceCode);
    }

    private String sendEnrollCommand(IotDevice device, Long requestedBy, boolean active, Instant now) {
        String commandId = UUID.randomUUID().toString().replace("-", "");
        int ttl = properties.getDeviceCommandTtlSeconds() > 0 ? properties.getDeviceCommandTtlSeconds() : 10;
        Instant expiresAt = now.plusSeconds(ttl);
        Map<String, Object> params = active
                ? Map.of("active", true, "seconds", WINDOW.toSeconds())
                : Map.of("active", false);
        commandRepository.save(new DeviceCommand(
                commandId,
                device.getId(),
                DeviceCommandType.ENROLL,
                active ? "{\"active\":true,\"seconds\":" + WINDOW.toSeconds() + "}" : "{\"active\":false}",
                requestedBy,
                now,
                expiresAt
        ));
        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "DeviceCommandRequested",
                device.getBuildingId(),
                device.getGatewayCode(),
                null,
                Map.of(
                        "commandId", commandId,
                        "deviceCode", device.getDeviceCode(),
                        "type", "enroll",
                        "params", params,
                        "expiresAt", expiresAt.toString()
                )
        );
        String gatewayCode = device.getGatewayCode();
        // Igual que el LED: se publica cuando la transacción confirma.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    edgeCommandPublisher.publish(gatewayCode, "device-command", envelope);
                }
            });
        } else {
            edgeCommandPublisher.publish(gatewayCode, "device-command", envelope);
        }
        return commandId;
    }
}
