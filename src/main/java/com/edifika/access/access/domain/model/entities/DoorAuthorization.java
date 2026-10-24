package com.edifika.access.access.domain.model.entities;

import com.edifika.access.access.domain.model.valueobjects.AuthorizationStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "door_authorizations")
public class DoorAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthorizationStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected DoorAuthorization() {
    }

    public DoorAuthorization(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("El ID de usuario es obligatorio");
        }
        this.userId = userId;
        this.status = AuthorizationStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        this.status = AuthorizationStatus.REVOKED;
    }

    public boolean isActive() {
        return this.status == AuthorizationStatus.ACTIVE;
    }
}
