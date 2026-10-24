package com.edifika.access.access.domain.model.entities;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "resident_statuses")
public class ResidentStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Long buildingId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resident_status_units", joinColumns = @JoinColumn(name = "resident_status_id"))
    @Column(name = "unit_id", nullable = false)
    private Set<Long> unitIds = new HashSet<>();

    @Column(nullable = false)
    private boolean delinquent;

    @Column(nullable = false)
    private int overdueDebtCount;

    @Column(nullable = false)
    private Instant checkedAt;

    @Column(length = 255)
    private String lastError;

    protected ResidentStatus() {
    }

    public ResidentStatus(Long userId, Long buildingId, Set<Long> unitIds, boolean delinquent, int overdueDebtCount, Instant checkedAt) {
        this.userId = userId;
        this.buildingId = buildingId;
        this.unitIds = unitIds != null ? new HashSet<>(unitIds) : new HashSet<>();
        this.delinquent = delinquent;
        this.overdueDebtCount = overdueDebtCount;
        this.checkedAt = checkedAt;
        this.lastError = null;
    }

    public void markChecked(boolean delinquent, int overdueDebtCount, Instant checkedAt, Set<Long> unitIds, Long buildingId) {
        this.delinquent = delinquent;
        this.overdueDebtCount = overdueDebtCount;
        this.checkedAt = checkedAt;
        if (unitIds != null) {
            this.unitIds = new HashSet<>(unitIds);
        }
        if (buildingId != null) {
            this.buildingId = buildingId;
        }
        this.lastError = null;
    }

    public void markError(String error, Instant checkedAt) {
        this.lastError = error;
        this.checkedAt = checkedAt;
    }
}
