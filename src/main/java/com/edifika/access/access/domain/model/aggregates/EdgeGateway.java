package com.edifika.access.access.domain.model.aggregates;

import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.GatewayStatus;
import com.edifika.access.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Getter
@Entity
@Table(name = "edge_gateways")
public class EdgeGateway extends AuditableAbstractAggregateRoot<EdgeGateway> {

    @Column(nullable = false, unique = true, length = 64)
    private String gatewayCode;

    @Column(nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GatewayStatus status;

    @Column(nullable = false, length = 64)
    private String apiKeyHash;

    @Column(nullable = false)
    private long syncVersion;

    @Column
    private Instant lastSyncAt;

    protected EdgeGateway() {
    }

    public record ApiKeyIssue(EdgeGateway gateway, String plainApiKey) {
    }

    public static ApiKeyIssue issue(CreateGatewayCommand command) {
        EdgeGateway gateway = new EdgeGateway();
        gateway.gatewayCode = command.gatewayCode();
        gateway.buildingId = command.buildingId();
        gateway.name = command.name();
        gateway.status = GatewayStatus.PROVISIONED;
        gateway.syncVersion = 1L;

        String plainKey = generateApiKey();
        gateway.apiKeyHash = hashApiKey(plainKey);
        return new ApiKeyIssue(gateway, plainKey);
    }

    public String rotateApiKey() {
        String newPlainKey = generateApiKey();
        this.apiKeyHash = hashApiKey(newPlainKey);
        return newPlainKey;
    }

    public boolean apiKeyMatches(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            return false;
        }
        String computedHash = hashApiKey(plainApiKey);
        return MessageDigest.isEqual(
                this.apiKeyHash.getBytes(StandardCharsets.UTF_8),
                computedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    public long bumpSyncVersion() {
        this.syncVersion++;
        return this.syncVersion;
    }

    public void markSynced(Instant now) {
        this.lastSyncAt = now;
        if (this.status == GatewayStatus.OFFLINE || this.status == GatewayStatus.PROVISIONED) {
            this.status = GatewayStatus.ONLINE;
        }
    }

    public void decommission() {
        this.status = GatewayStatus.DECOMMISSIONED;
    }

    public boolean isOnline() {
        return this.status == GatewayStatus.ONLINE;
    }

    public boolean isDecommissioned() {
        return this.status == GatewayStatus.DECOMMISSIONED;
    }

    private static String generateApiKey() {
        byte[] randomBytes = new byte[33];
        new SecureRandom().nextBytes(randomBytes);
        String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return "egk_" + base64;
    }

    private static String hashApiKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
