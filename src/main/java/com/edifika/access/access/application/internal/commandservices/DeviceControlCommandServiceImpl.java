package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.domain.exceptions.DeviceNotFoundException;
import com.edifika.access.access.domain.exceptions.ReservationConflictException;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.SetLedColorCommand;
import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.*;
import com.edifika.access.access.domain.services.DeviceControlCommandService;
import com.edifika.access.access.domain.services.ReservationQueryService;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class DeviceControlCommandServiceImpl implements DeviceControlCommandService {

    private final DeviceCommandRepository commandRepository;
    private final IotDeviceRepository deviceRepository;
    private final ReservationQueryService reservationQueryService;
    private final EdgeCommandPublisher edgeCommandPublisher;
    private final IntegrationProperties properties;

    public DeviceControlCommandServiceImpl(
            DeviceCommandRepository commandRepository,
            IotDeviceRepository deviceRepository,
            ReservationQueryService reservationQueryService,
            EdgeCommandPublisher edgeCommandPublisher,
            IntegrationProperties properties) {
        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
        this.reservationQueryService = reservationQueryService;
        this.edgeCommandPublisher = edgeCommandPublisher;
        this.properties = properties;
    }

    @Override
    public DeviceCommand issueSetLedColor(SetLedColorCommand command) {
        IotDevice device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFoundException(command.deviceId()));

        if (!device.hasCapability(DeviceCapability.RGB_LED)) {
            throw new IllegalArgumentException("El dispositivo no tiene capacidad RGB_LED");
        }

        if (device.getStatus() != DeviceStatus.ACTIVE) {
            throw new ReservationConflictException("El dispositivo no está activo");
        }

        if (device.getAccessMode() != AccessMode.RESERVED) {
            throw new ReservationConflictException("La puerta no está reservada");
        }

        Instant now = Instant.now();
        if (!command.actorIsAdmin()) {
            Optional<DoorReservation> activeRes = reservationQueryService.activeReservation(device.getId(), now);
            if (activeRes.isEmpty() || !activeRes.get().getHolderUserId().equals(command.actorUserId())) {
                throw new AccessDeniedException("Solo el titular de la reserva activa o un administrador pueden cambiar el color del LED");
            }
        }

        RgbColor rgb = new RgbColor(command.color());
        String commandId = UUID.randomUUID().toString().replace("-", "");
        int ttl = properties.getDeviceCommandTtlSeconds() > 0 ? properties.getDeviceCommandTtlSeconds() : 10;
        Instant expiresAt = now.plusSeconds(ttl);

        DeviceCommand deviceCommand = new DeviceCommand(
                commandId,
                device.getId(),
                DeviceCommandType.SET_LED,
                "{\"color\":\"" + rgb.value() + "\"}",
                command.actorUserId(),
                now,
                expiresAt
        );

        DeviceCommand saved = commandRepository.save(deviceCommand);

        String gatewayCode = device.getGatewayCode();
        Long buildingId = device.getBuildingId();
        String deviceCode = device.getDeviceCode();

        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "DeviceCommandRequested",
                buildingId,
                gatewayCode,
                null,
                Map.of(
                        "commandId", commandId,
                        "deviceCode", deviceCode,
                        "type", "rgb",
                        "params", Map.of("color", rgb.value()),
                        "expiresAt", expiresAt.toString()
                )
        );

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

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceCommand> getCommand(String commandId) {
        return commandRepository.findByCommandId(commandId);
    }
}
