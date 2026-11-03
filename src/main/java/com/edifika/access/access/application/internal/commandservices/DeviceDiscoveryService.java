package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.domain.model.aggregates.DiscoveredDevice;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.DiscoveryStatus;
import com.edifika.access.access.domain.services.DeviceCommandService;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DiscoveredDeviceRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Descubrimiento y adopción de nodos: un gateway identifica un nodo que se anunció sin
 * estar registrado (evento {@code DeviceDiscovered}), queda pendiente en el edificio de
 * ese gateway y un administrador lo adopta completando sus datos.
 */
@Slf4j
@Service
@Transactional
public class DeviceDiscoveryService {

    public static final int DEFAULT_IDENTIFY_SECONDS = 10;
    public static final int MAX_IDENTIFY_SECONDS = 60;

    private final DiscoveredDeviceRepository discoveredRepository;
    private final EdgeGatewayRepository gatewayRepository;
    private final IotDeviceRepository deviceRepository;
    private final DeviceCommandService deviceCommandService;
    private final EdgeCommandPublisher edgeCommandPublisher;
    private final IntegrationProperties properties;
    private final Clock clock;

    @Autowired
    public DeviceDiscoveryService(
            DiscoveredDeviceRepository discoveredRepository,
            EdgeGatewayRepository gatewayRepository,
            IotDeviceRepository deviceRepository,
            DeviceCommandService deviceCommandService,
            EdgeCommandPublisher edgeCommandPublisher,
            IntegrationProperties properties) {
        this(discoveredRepository, gatewayRepository, deviceRepository, deviceCommandService,
                edgeCommandPublisher, properties, Clock.systemUTC());
    }

    DeviceDiscoveryService(
            DiscoveredDeviceRepository discoveredRepository,
            EdgeGatewayRepository gatewayRepository,
            IotDeviceRepository deviceRepository,
            DeviceCommandService deviceCommandService,
            EdgeCommandPublisher edgeCommandPublisher,
            IntegrationProperties properties,
            Clock clock) {
        this.discoveredRepository = discoveredRepository;
        this.gatewayRepository = gatewayRepository;
        this.deviceRepository = deviceRepository;
        this.deviceCommandService = deviceCommandService;
        this.edgeCommandPublisher = edgeCommandPublisher;
        this.properties = properties;
        this.clock = clock;
    }

    /** Datos que el gateway informa del nodo (payload de {@code DeviceDiscovered}). */
    public record Discovery(String deviceCode, String mac, String firmware, String model, Collection<String> capabilities,
                            String ip, Integer rssi, String pairingCode, Instant firstSeenAt, Instant lastSeenAt) {
    }

    /** Datos que completa el administrador al adoptar. */
    public record Adoption(String name, String location, DeviceType deviceType, Set<DeviceCapability> capabilities,
                           int unlockSeconds) {
    }

    /**
     * Un gateway identificó un nodo. Se ignora si el código ya es un dispositivo registrado
     * (el gateway aún no recibió el sync) o si el gateway no existe.
     */
    public void onDeviceDiscovered(String gatewayCode, Discovery discovery) {
        if (discovery.deviceCode() == null || discovery.deviceCode().isBlank()) {
            log.warn("DeviceDiscovered sin deviceCode desde {}; se ignora", gatewayCode);
            return;
        }
        EdgeGateway gateway = gatewayCode == null ? null : gatewayRepository.findByGatewayCode(gatewayCode).orElse(null);
        if (gateway == null) {
            log.warn("DeviceDiscovered de un gateway desconocido ({}); se ignora", gatewayCode);
            return;
        }
        if (deviceRepository.existsByDeviceCode(discovery.deviceCode())) {
            log.debug("{} ya está registrado; DeviceDiscovered ignorado", discovery.deviceCode());
            return;
        }
        Instant firstSeen = discovery.firstSeenAt() != null ? discovery.firstSeenAt() : clock.instant();
        DiscoveredDevice discovered = discoveredRepository.findByDeviceCode(discovery.deviceCode())
                .orElseGet(() -> new DiscoveredDevice(discovery.deviceCode(), gateway, firstSeen));
        discovered.refresh(gateway, discovery.mac(), discovery.firmware(), discovery.model(), discovery.capabilities(),
                discovery.ip(), discovery.rssi(), discovery.pairingCode(),
                discovery.lastSeenAt() != null ? discovery.lastSeenAt() : clock.instant());
        discoveredRepository.save(discovered);
    }

    @Transactional(readOnly = true)
    public List<DiscoveredDevice> list(Long buildingId, DiscoveryStatus status) {
        DiscoveryStatus wanted = status != null ? status : DiscoveryStatus.PENDING;
        return buildingId != null
                ? discoveredRepository.findAllByBuildingIdAndStatusOrderByLastSeenAtDesc(buildingId, wanted)
                : discoveredRepository.findAllByStatusOrderByLastSeenAtDesc(wanted);
    }

    /** Hace parpadear y sonar al nodo para que el administrador reconozca la placa. */
    public void identify(Long discoveredId, Integer seconds, Long requestedBy) {
        DiscoveredDevice discovered = require(discoveredId);
        discovered.requirePending();
        int duration = seconds == null ? DEFAULT_IDENTIFY_SECONDS : seconds;
        if (duration < 1 || duration > MAX_IDENTIFY_SECONDS) {
            throw new IllegalArgumentException("La duración debe estar entre 1 y " + MAX_IDENTIFY_SECONDS + " segundos");
        }
        Instant now = clock.instant();
        int ttl = properties.getDeviceCommandTtlSeconds() > 0 ? properties.getDeviceCommandTtlSeconds() : 10;
        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "DeviceCommandRequested",
                discovered.getBuildingId(),
                discovered.getGatewayCode(),
                null,
                Map.of(
                        "commandId", UUID.randomUUID().toString().replace("-", ""),
                        "deviceCode", discovered.getDeviceCode(),
                        "type", "identify",
                        "params", Map.of("seconds", duration),
                        "expiresAt", now.plusSeconds(ttl).toString()
                )
        );
        log.info("Identificando {} a pedido de {}", discovered.getDeviceCode(), requestedBy);
        publishAfterCommit(discovered.getGatewayCode(), envelope);
    }

    /**
     * Crea el dispositivo con los datos del administrador en el gateway que lo identificó.
     * El gateway lo recibe en el siguiente sync y el nodo sale del estado sin registrar con
     * su primer heartbeat.
     */
    public IotDevice adopt(Long discoveredId, Adoption adoption) {
        DiscoveredDevice discovered = require(discoveredId);
        discovered.requirePending();
        IotDevice device = deviceCommandService.createDevice(new CreateDeviceCommand(
                discovered.getDeviceCode(),
                discovered.getGatewayId(),
                adoption.name(),
                adoption.location(),
                adoption.deviceType(),
                adoption.capabilities(),
                adoption.unlockSeconds()
        )).orElseThrow(() -> new IllegalStateException("No se pudo crear el dispositivo"));
        discovered.markAdopted(device.getId());
        discoveredRepository.save(discovered);
        return device;
    }

    public DiscoveredDevice ignore(Long discoveredId) {
        DiscoveredDevice discovered = require(discoveredId);
        discovered.ignore();
        return discoveredRepository.save(discovered);
    }

    private DiscoveredDevice require(Long discoveredId) {
        return discoveredRepository.findById(discoveredId)
                .orElseThrow(() -> new DiscoveredDeviceNotFoundException(discoveredId));
    }

    private void publishAfterCommit(String gatewayCode, IntegrationEnvelope envelope) {
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
    }

    /** 404 en el manejador global (su nombre termina en NotFoundException). */
    public static class DiscoveredDeviceNotFoundException extends RuntimeException {
        public DiscoveredDeviceNotFoundException(Long id) {
            super("No existe el dispositivo descubierto " + id);
        }
    }
}
