package com.edifika.access.access.application.internal.edge;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.access.interfaces.rest.resources.edge.EdgeCredentialItem;
import com.edifika.access.access.interfaces.rest.resources.edge.EdgeDeviceItem;
import com.edifika.access.access.interfaces.rest.resources.edge.EdgeReservationItem;
import com.edifika.access.access.interfaces.rest.resources.edge.EdgeSyncResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class EdgeSnapshotService {

    private final AccessCredentialRepository credentialRepository;
    private final IotDeviceRepository deviceRepository;
    private final DoorReservationRepository reservationRepository;

    public EdgeSnapshotService(
            AccessCredentialRepository credentialRepository,
            IotDeviceRepository deviceRepository,
            DoorReservationRepository reservationRepository) {
        this.credentialRepository = credentialRepository;
        this.deviceRepository = deviceRepository;
        this.reservationRepository = reservationRepository;
    }

    public EdgeSyncResource snapshot(EdgeGateway gateway, Long since) {
        if (since != null && since == gateway.getSyncVersion()) {
            return EdgeSyncResource.emptyIncrement(gateway.getSyncVersion());
        }

        List<AccessCredential> credentials = credentialRepository.findAllByBuildingId(gateway.getBuildingId());
        List<EdgeCredentialItem> credentialItems = credentials.stream()
                .map(c -> new EdgeCredentialItem(
                        c.getRfidUid(),
                        "RFID",
                        c.residentRef(),
                        c.getHolderName() != null ? c.getHolderName() : "",
                        c.edgeStatus(),
                        c.getValidFrom(),
                        c.getValidUntil()
                ))
                .toList();

        List<IotDevice> devices = deviceRepository.findAllByGatewayId(gateway.getId()).stream()
                .filter(d -> d.getStatus() != DeviceStatus.DECOMMISSIONED)
                .toList();

        List<EdgeDeviceItem> deviceItems = devices.stream()
                .map(d -> {
                    String ledColor = d.getAccessMode() == AccessMode.RESERVED ? d.getLedColor() : null;
                    return new EdgeDeviceItem(
                            d.getDeviceCode(),
                            d.getName(),
                            d.getLocation(),
                            d.getCapabilities().stream().map(DeviceCapability::name).sorted().toList(),
                            ledColor,
                            d.getStatus().name(),
                            Map.of()
                    );
                })
                .toList();

        List<EdgeReservationItem> reservationItems = new ArrayList<>();
        Instant now = Instant.now();
        for (IotDevice dev : devices) {
            List<DoorReservation> devRes = reservationRepository.findActiveOrUpcomingForDevice(dev.getId(), now);
            for (DoorReservation r : devRes) {
                if (r.getStatus() == ReservationStatus.ACTIVE || r.getStatus() == ReservationStatus.SCHEDULED) {
                    reservationItems.add(new EdgeReservationItem(
                            r.getId().toString(),
                            dev.getDeviceCode(),
                            r.getHolderUserId().toString(),
                            r.getStartsAt(),
                            r.getEndsAt(),
                            r.getActiveGuestUserIds().stream().map(Object::toString).toList()
                    ));
                }
            }
        }

        return new EdgeSyncResource(
                gateway.getSyncVersion(),
                true,
                credentialItems,
                deviceItems,
                List.of(),
                reservationItems,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
