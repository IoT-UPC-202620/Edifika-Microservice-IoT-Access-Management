package com.edifika.access.access.domain.model.aggregates;

import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.UpdateDeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.*;
import com.edifika.access.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "iot_devices")
public class IotDevice extends AuditableAbstractAggregateRoot<IotDevice> {

    @Column(nullable = false, unique = true, length = 64)
    private String deviceCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gateway_id", nullable = false)
    private EdgeGateway gateway;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceType deviceType;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "iot_device_capabilities", joinColumns = @JoinColumn(name = "device_id"))
    @Column(name = "capability", nullable = false, length = 32)
    private Set<DeviceCapability> capabilities = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccessMode accessMode;

    @Column
    private Long activeReservationId;

    @Column(length = 7)
    private String ledColor;

    @Column(nullable = false)
    private int unlockSeconds;

    @Column(length = 64)
    private String firmwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceStatus status;

    @Column
    private Instant lastSeenAt;

    protected IotDevice() {
    }

    public IotDevice(CreateDeviceCommand command, EdgeGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("El gateway es obligatorio");
        }
        validateDoorCapabilities(command.deviceType(), command.capabilities());

        this.deviceCode = DeviceCode.of(command.deviceCode()).value();
        this.gateway = gateway;
        this.name = command.name();
        this.location = command.location();
        this.deviceType = command.deviceType();
        this.capabilities = new HashSet<>(command.capabilities());
        this.accessMode = AccessMode.FREE_ACCESS;
        this.activeReservationId = null;
        this.ledColor = null;
        this.unlockSeconds = command.unlockSeconds();
        this.status = DeviceStatus.PROVISIONED;
        this.firmwareVersion = null;
        this.lastSeenAt = null;
    }

    public void update(UpdateDeviceCommand command) {
        if (command.name() != null && !command.name().isBlank()) {
            this.name = command.name();
        }
        if (command.location() != null && !command.location().isBlank()) {
            this.location = command.location();
        }
        if (command.capabilities() != null && !command.capabilities().isEmpty()) {
            validateDoorCapabilities(this.deviceType, command.capabilities());
            this.capabilities = new HashSet<>(command.capabilities());
        }
        if (command.unlockSeconds() != null && command.unlockSeconds() > 0) {
            this.unlockSeconds = command.unlockSeconds();
        }
    }

    public void setLedColor(RgbColor color) {
        this.ledColor = color != null ? color.value() : null;
    }

    public void clearLedColor() {
        this.ledColor = null;
    }

    public void markOnline(Instant now) {
        this.lastSeenAt = now;
        this.status = DeviceStatus.ACTIVE;
    }

    public void markOffline(Instant now) {
        this.lastSeenAt = now;
        this.status = DeviceStatus.OFFLINE;
    }

    public void startMaintenance() {
        this.status = DeviceStatus.MAINTENANCE;
    }

    public void endMaintenance(Instant now, Duration heartbeatTimeout) {
        if (this.lastSeenAt != null && this.lastSeenAt.isAfter(now.minus(heartbeatTimeout))) {
            this.status = DeviceStatus.ACTIVE;
        } else {
            this.status = DeviceStatus.OFFLINE;
        }
    }

    public void decommission() {
        this.status = DeviceStatus.DECOMMISSIONED;
    }

    public void updateFirmware(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public void setAccessMode(AccessMode accessMode, Long activeReservationId) {
        this.accessMode = accessMode;
        this.activeReservationId = activeReservationId;
    }

    public boolean isDoor() {
        return this.deviceType == DeviceType.DOOR;
    }

    public boolean hasCapability(DeviceCapability capability) {
        return this.capabilities != null && this.capabilities.contains(capability);
    }

    public Set<DeviceCapability> getCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    public Long getGatewayId() {
        return gateway != null ? gateway.getId() : null;
    }

    public String getGatewayCode() {
        return gateway != null ? gateway.getGatewayCode() : null;
    }

    public Long getBuildingId() {
        return gateway != null ? gateway.getBuildingId() : null;
    }

    private static void validateDoorCapabilities(DeviceType type, Set<DeviceCapability> caps) {
        if (type == DeviceType.DOOR) {
            if (caps == null || !caps.contains(DeviceCapability.RFID) || !caps.contains(DeviceCapability.LOCK)) {
                throw new IllegalArgumentException("Un dispositivo tipo DOOR requiere las capacidades RFID y LOCK");
            }
        }
    }
}
