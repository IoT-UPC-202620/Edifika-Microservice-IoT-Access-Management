package com.edifika.access.access.domain.model.entities;

import com.edifika.access.access.domain.model.valueobjects.DeviceCommandStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceCommandType;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "device_commands")
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String commandId;

    @Column(nullable = false)
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceCommandType type;

    @Column(nullable = false, columnDefinition = "text")
    private String params;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceCommandStatus status;

    @Column(length = 255)
    private String detail;

    @Column(nullable = false)
    private Long requestedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant resolvedAt;

    protected DeviceCommand() {
    }

    public DeviceCommand(
            String commandId,
            Long deviceId,
            DeviceCommandType type,
            String params,
            Long requestedBy,
            Instant requestedAt,
            Instant expiresAt) {
        this.commandId = commandId;
        this.deviceId = deviceId;
        this.type = type;
        this.params = params;
        this.status = DeviceCommandStatus.PENDING;
        this.detail = null;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
        this.resolvedAt = null;
    }

    public void markOk(Instant now) {
        this.status = DeviceCommandStatus.OK;
        this.resolvedAt = now;
    }

    public void markFailed(String detail, Instant now) {
        this.status = DeviceCommandStatus.FAILED;
        this.detail = detail;
        this.resolvedAt = now;
    }

    public void markExpired() {
        this.status = DeviceCommandStatus.EXPIRED;
        this.resolvedAt = Instant.now();
    }

    public boolean isPending() {
        return this.status == DeviceCommandStatus.PENDING;
    }
}
