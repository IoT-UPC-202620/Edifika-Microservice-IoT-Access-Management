package com.edifika.access.access.domain.model.entities;

import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.AccessReason;
import com.edifika.access.access.domain.model.valueobjects.AccessResult;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "access_attempts")
public class AccessAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String attemptId;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false, length = 64)
    private String deviceCode;

    @Column(nullable = false)
    private Long buildingId;

    @Column(length = 32)
    private String rfidUid;

    @Column
    private Long userId;

    @Column
    private String residentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccessResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccessReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccessMode accessMode;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, unique = true, length = 64)
    private String sourceEventId;

    @Column(nullable = false)
    private Instant createdAt;

    protected AccessAttempt() {
    }

    public AccessAttempt(
            String attemptId,
            Long deviceId,
            String deviceCode,
            Long buildingId,
            String rfidUid,
            Long userId,
            String residentName,
            AccessResult result,
            AccessReason reason,
            AccessMode accessMode,
            Instant occurredAt,
            String sourceEventId) {
        this.attemptId = attemptId;
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.buildingId = buildingId;
        this.rfidUid = rfidUid;
        this.userId = userId;
        this.residentName = residentName;
        this.result = result;
        this.reason = reason;
        this.accessMode = accessMode != null ? accessMode : AccessMode.FREE_ACCESS;
        this.occurredAt = occurredAt;
        this.sourceEventId = sourceEventId;
        this.createdAt = Instant.now();
    }
}
