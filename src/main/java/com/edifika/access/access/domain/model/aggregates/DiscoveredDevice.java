package com.edifika.access.access.domain.model.aggregates;

import com.edifika.access.access.domain.model.valueobjects.DiscoveryStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Nodo que un gateway identificó porque se anunció sin estar registrado. Pertenece al
 * edificio de ese gateway y queda pendiente (e inerte) hasta que un administrador lo
 * adopta, momento en que se crea el {@link IotDevice} con los datos que este completa.
 */
@Getter
@Entity
@Table(name = "discovered_devices", indexes = {
        @Index(name = "idx_discovered_devices_building", columnList = "buildingId, status")
})
public class DiscoveredDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String deviceCode;

    @Column(nullable = false)
    private Long gatewayId;

    @Column(nullable = false, length = 64)
    private String gatewayCode;

    @Column(nullable = false)
    private Long buildingId;

    @Column(nullable = false, length = 17)
    private String mac;

    @Column(nullable = false, length = 32)
    private String firmware;

    @Column(length = 64)
    private String model;

    /** Capacidades anunciadas, separadas por coma (RFID,LOCK,...). */
    @Column(nullable = false, length = 255)
    private String capabilities;

    @Column(length = 45)
    private String ip;

    @Column
    private Integer rssi;

    @Column(nullable = false, length = 4)
    private String pairingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscoveryStatus status;

    /** Dispositivo creado al adoptarlo. */
    @Column
    private Long deviceId;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    protected DiscoveredDevice() {
    }

    public DiscoveredDevice(String deviceCode, EdgeGateway gateway, Instant firstSeenAt) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("El código del nodo es obligatorio");
        }
        this.deviceCode = deviceCode;
        this.gatewayId = gateway.getId();
        this.gatewayCode = gateway.getGatewayCode();
        this.buildingId = gateway.getBuildingId();
        this.status = DiscoveryStatus.PENDING;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = firstSeenAt;
    }

    /**
     * Actualiza lo que el nodo informa de sí mismo. Si ahora lo escucha otro gateway, pasa
     * al edificio de ese gateway. El estado no cambia: uno ignorado sigue ignorado.
     */
    public void refresh(EdgeGateway gateway, String mac, String firmware, String model, Collection<String> capabilities,
                        String ip, Integer rssi, String pairingCode, Instant lastSeenAt) {
        this.gatewayId = gateway.getId();
        this.gatewayCode = gateway.getGatewayCode();
        this.buildingId = gateway.getBuildingId();
        this.mac = mac;
        this.firmware = firmware;
        this.model = model;
        this.capabilities = capabilities == null ? "" : String.join(",", capabilities);
        this.ip = ip;
        this.rssi = rssi;
        this.pairingCode = pairingCode;
        if (lastSeenAt != null && (this.lastSeenAt == null || lastSeenAt.isAfter(this.lastSeenAt))) {
            this.lastSeenAt = lastSeenAt;
        }
    }

    public List<String> getCapabilityList() {
        if (capabilities == null || capabilities.isBlank()) {
            return List.of();
        }
        return Arrays.stream(capabilities.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isPending() {
        return status == DiscoveryStatus.PENDING;
    }

    /** Falla si ya fue adoptado o ignorado: identificar o adoptar solo aplica a pendientes. */
    public void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("El dispositivo descubierto ya no está pendiente");
        }
    }

    public void markAdopted(Long deviceId) {
        requirePending();
        this.deviceId = deviceId;
        this.status = DiscoveryStatus.ADOPTED;
    }

    public void ignore() {
        if (status == DiscoveryStatus.PENDING) {
            this.status = DiscoveryStatus.IGNORED;
        }
    }
}
