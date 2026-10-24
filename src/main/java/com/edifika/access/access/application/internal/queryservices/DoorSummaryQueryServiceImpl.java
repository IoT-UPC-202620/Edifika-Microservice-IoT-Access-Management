package com.edifika.access.access.application.internal.queryservices;

import com.edifika.access.access.application.internal.debts.DebtCheckService;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.entities.ResidentStatus;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.services.DoorSummaryQueryService;
import com.edifika.access.access.domain.services.ReservationQueryService;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ResidentStatusRepository;
import com.edifika.access.access.interfaces.rest.resources.CurrentReservationSummaryResource;
import com.edifika.access.access.interfaces.rest.resources.DoorSummaryResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DoorSummaryQueryServiceImpl implements DoorSummaryQueryService {

    private final IotDeviceRepository deviceRepository;
    private final ResidentStatusRepository residentStatusRepository;
    private final ReservationQueryService reservationQueryService;
    private final DebtCheckService debtCheckService;

    public DoorSummaryQueryServiceImpl(
            IotDeviceRepository deviceRepository,
            ResidentStatusRepository residentStatusRepository,
            ReservationQueryService reservationQueryService,
            DebtCheckService debtCheckService) {
        this.deviceRepository = deviceRepository;
        this.residentStatusRepository = residentStatusRepository;
        this.reservationQueryService = reservationQueryService;
        this.debtCheckService = debtCheckService;
    }

    @Override
    public List<DoorSummaryResource> doorsFor(AuthenticatedUser user, Instant now) {
        boolean isAdmin = user != null && user.isAdmin();
        Long userId = user != null ? user.userId() : null;

        List<IotDevice> doors;
        Optional<ResidentStatus> residentStatusOpt = Optional.empty();

        if (isAdmin) {
            doors = deviceRepository.search(null, null, DeviceType.DOOR, null);
        } else if (userId != null) {
            residentStatusOpt = residentStatusRepository.findByUserId(userId);
            if (residentStatusOpt.isEmpty()) {
                try {
                    ResidentStatus refreshed = debtCheckService.refreshUser(userId);
                    residentStatusOpt = Optional.ofNullable(refreshed);
                } catch (Exception ignored) {
                }
            }

            if (residentStatusOpt.isPresent()) {
                Long buildingId = residentStatusOpt.get().getBuildingId();
                doors = deviceRepository.search(null, buildingId, DeviceType.DOOR, null);
            } else {
                doors = List.of();
            }
        } else {
            doors = List.of();
        }

        boolean delinquent = residentStatusOpt.map(ResidentStatus::isDelinquent).orElse(false);

        List<DoorSummaryResource> results = new ArrayList<>();
        for (IotDevice door : doors) {
            Optional<DoorReservation> activeRes = reservationQueryService.activeReservation(door.getId(), now);
            AccessMode mode = activeRes.isPresent() ? AccessMode.RESERVED : AccessMode.FREE_ACCESS;

            CurrentReservationSummaryResource currentReservation = null;
            if (activeRes.isPresent()) {
                DoorReservation res = activeRes.get();
                boolean mine = userId != null && userId.equals(res.getHolderUserId());
                currentReservation = new CurrentReservationSummaryResource(
                        res.getId(),
                        res.getStartsAt(),
                        res.getEndsAt(),
                        mine
                );
            }

            boolean canEnter;
            if (delinquent) {
                canEnter = false;
            } else if (mode == AccessMode.FREE_ACCESS) {
                canEnter = true;
            } else {
                canEnter = isAdmin || (activeRes.isPresent() && activeRes.get().isAuthorized(userId));
            }

            boolean canReserve;
            if (delinquent) {
                canReserve = false;
            } else {
                canReserve = door.getStatus() != DeviceStatus.DECOMMISSIONED;
            }

            boolean canChangeLed = door.hasCapability(DeviceCapability.RGB_LED)
                    && mode == AccessMode.RESERVED
                    && (isAdmin || (activeRes.isPresent() && activeRes.get().getHolderUserId().equals(userId)));

            String visibleLedColor = null;
            if (canChangeLed || (activeRes.isPresent() && activeRes.get().isAuthorized(userId))) {
                visibleLedColor = door.getLedColor();
            }

            results.add(new DoorSummaryResource(
                    door.getId(),
                    door.getDeviceCode(),
                    door.getName(),
                    door.getLocation(),
                    door.getBuildingId(),
                    door.getStatus().name(),
                    mode.name(),
                    currentReservation,
                    visibleLedColor,
                    canEnter,
                    canReserve,
                    canChangeLed
            ));
        }

        return results;
    }
}
