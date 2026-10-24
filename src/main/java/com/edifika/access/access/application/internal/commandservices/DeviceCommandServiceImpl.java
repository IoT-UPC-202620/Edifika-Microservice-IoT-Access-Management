package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.DeviceNotFoundException;
import com.edifika.access.access.domain.exceptions.DuplicateDeviceCodeException;
import com.edifika.access.access.domain.exceptions.GatewayNotFoundException;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.DecommissionDeviceCommand;
import com.edifika.access.access.domain.model.commands.SetDeviceMaintenanceCommand;
import com.edifika.access.access.domain.model.commands.UpdateDeviceCommand;
import com.edifika.access.access.domain.model.events.DeviceDecommissionedEvent;
import com.edifika.access.access.domain.model.events.DeviceRegisteredEvent;
import com.edifika.access.access.domain.model.events.DeviceUpdatedEvent;
import com.edifika.access.access.domain.services.DeviceCommandService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@Transactional
public class DeviceCommandServiceImpl implements DeviceCommandService {

    private final IotDeviceRepository deviceRepository;
    private final EdgeGatewayRepository gatewayRepository;
    private final EdgeSyncNotifier edgeSyncNotifier;
    private final ApplicationEventPublisher eventPublisher;

    public DeviceCommandServiceImpl(
            IotDeviceRepository deviceRepository,
            EdgeGatewayRepository gatewayRepository,
            EdgeSyncNotifier edgeSyncNotifier,
            ApplicationEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.gatewayRepository = gatewayRepository;
        this.edgeSyncNotifier = edgeSyncNotifier;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<IotDevice> createDevice(CreateDeviceCommand command) {
        if (deviceRepository.existsByDeviceCode(command.deviceCode())) {
            throw new DuplicateDeviceCodeException(command.deviceCode());
        }

        EdgeGateway gateway = gatewayRepository.findById(command.gatewayId())
                .orElseThrow(() -> new GatewayNotFoundException(command.gatewayId()));

        IotDevice device = new IotDevice(command, gateway);
        IotDevice saved = deviceRepository.save(device);

        edgeSyncNotifier.changed(gateway.getId());

        eventPublisher.publishEvent(new DeviceRegisteredEvent(
                saved.getId(),
                saved.getDeviceCode(),
                saved.getGatewayCode(),
                saved.getBuildingId(),
                saved.getName(),
                saved.getDeviceType(),
                saved.getCapabilities(),
                saved.getAccessMode(),
                saved.getStatus()
        ));

        return Optional.of(saved);
    }

    @Override
    public Optional<IotDevice> updateDevice(UpdateDeviceCommand command) {
        IotDevice device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFoundException(command.deviceId()));

        device.update(command);
        IotDevice saved = deviceRepository.save(device);

        edgeSyncNotifier.changed(saved.getGatewayId());

        eventPublisher.publishEvent(new DeviceUpdatedEvent(
                saved.getId(),
                saved.getDeviceCode(),
                saved.getGatewayCode(),
                saved.getBuildingId(),
                saved.getName(),
                saved.getDeviceType(),
                saved.getCapabilities(),
                saved.getAccessMode(),
                saved.getStatus()
        ));

        return Optional.of(saved);
    }

    @Override
    public Optional<IotDevice> setMaintenance(SetDeviceMaintenanceCommand command) {
        IotDevice device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFoundException(command.deviceId()));

        if (command.enabled()) {
            device.startMaintenance();
        } else {
            device.endMaintenance(Instant.now(), Duration.ofSeconds(30));
        }

        IotDevice saved = deviceRepository.save(device);
        edgeSyncNotifier.changed(saved.getGatewayId());

        eventPublisher.publishEvent(new DeviceUpdatedEvent(
                saved.getId(),
                saved.getDeviceCode(),
                saved.getGatewayCode(),
                saved.getBuildingId(),
                saved.getName(),
                saved.getDeviceType(),
                saved.getCapabilities(),
                saved.getAccessMode(),
                saved.getStatus()
        ));

        return Optional.of(saved);
    }

    @Override
    public void decommissionDevice(DecommissionDeviceCommand command) {
        IotDevice device = deviceRepository.findById(command.deviceId())
                .orElseThrow(() -> new DeviceNotFoundException(command.deviceId()));

        device.decommission();
        IotDevice saved = deviceRepository.save(device);

        edgeSyncNotifier.changed(saved.getGatewayId());

        eventPublisher.publishEvent(new DeviceDecommissionedEvent(
                saved.getId(),
                saved.getDeviceCode(),
                saved.getBuildingId()
        ));
    }
}
