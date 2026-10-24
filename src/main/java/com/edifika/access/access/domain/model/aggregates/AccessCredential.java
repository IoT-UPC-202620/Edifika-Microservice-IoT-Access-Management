package com.edifika.access.access.domain.model.aggregates;

import com.edifika.access.access.domain.model.valueobjects.CredentialOrigin;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.access.domain.model.valueobjects.SuspensionReason;
import com.edifika.access.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "access_credentials")
public class AccessCredential extends AuditableAbstractAggregateRoot<AccessCredential> {

    @Column(nullable = false, unique = true, length = 64)
    private String rfidUid;

    @Column(nullable = false)
    private Long buildingId;

    @Column
    private Long userId;

    @Column
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CredentialStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SuspensionReason suspensionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CredentialOrigin origin;

    @Column
    private String edgeResidentRef;

    @Column
    private Instant validFrom;

    @Column
    private Instant validUntil;

    protected AccessCredential() {
    }

    public static AccessCredential createCloud(
            String rfidUid,
            Long buildingId,
            Long userId,
            String holderName,
            Instant validFrom,
            Instant validUntil) {
        AccessCredential credential = new AccessCredential();
        credential.rfidUid = new RfidUid(rfidUid).value();
        credential.buildingId = buildingId;
        credential.userId = userId;
        credential.holderName = holderName;
        credential.status = CredentialStatus.ACTIVE;
        credential.suspensionReason = null;
        credential.origin = CredentialOrigin.CLOUD;
        credential.edgeResidentRef = null;
        credential.validFrom = validFrom;
        credential.validUntil = validUntil;
        return credential;
    }

    public static AccessCredential createFromEdge(
            String rfidUid,
            String edgeResidentRef,
            CredentialStatus status,
            Long buildingId) {
        AccessCredential credential = new AccessCredential();
        credential.rfidUid = new RfidUid(rfidUid).value();
        credential.buildingId = buildingId;
        credential.userId = null;
        credential.holderName = null;
        credential.status = status;
        credential.suspensionReason = null;
        credential.origin = CredentialOrigin.EDGE;
        credential.edgeResidentRef = edgeResidentRef;
        credential.validFrom = null;
        credential.validUntil = null;
        return credential;
    }

    public void suspendForDebt() {
        if (this.status == CredentialStatus.ACTIVE) {
            this.status = CredentialStatus.SUSPENDED;
            this.suspensionReason = SuspensionReason.DELINQUENT;
        }
    }

    public void clearDebtSuspension() {
        if (this.suspensionReason == SuspensionReason.DELINQUENT) {
            this.status = CredentialStatus.ACTIVE;
            this.suspensionReason = null;
        }
    }

    public void changeStatus(CredentialStatus newStatus, Instant now) {
        this.status = newStatus;
        this.suspensionReason = null;
    }

    public void updateStatusFromEdge(CredentialStatus newStatus) {
        this.status = newStatus;
    }

    public boolean isUsableAt(Instant now) {
        if (this.status != CredentialStatus.ACTIVE) {
            return false;
        }
        if (this.validFrom != null && now.isBefore(this.validFrom)) {
            return false;
        }
        if (this.validUntil != null && now.isAfter(this.validUntil)) {
            return false;
        }
        return true;
    }

    public String edgeStatus() {
        return this.status == CredentialStatus.LOST ? "REVOKED" : this.status.name();
    }

    public String residentRef() {
        return this.userId != null ? this.userId.toString() : this.edgeResidentRef;
    }

    public boolean isSuspendedForDebt() {
        return this.status == CredentialStatus.SUSPENDED && this.suspensionReason == SuspensionReason.DELINQUENT;
    }
}
