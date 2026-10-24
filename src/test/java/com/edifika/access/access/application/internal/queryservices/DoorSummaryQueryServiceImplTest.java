package com.edifika.access.access.application.internal.queryservices;

import com.edifika.access.access.application.internal.debts.DebtCheckService;
import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.entities.ResidentStatus;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.RgbColor;
import com.edifika.access.access.domain.services.ReservationQueryService;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ResidentStatusRepository;
import com.edifika.access.access.interfaces.rest.resources.DoorSummaryResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoorSummaryQueryServiceImplTest {

    private IotDeviceRepository deviceRepository;
    private ResidentStatusRepository residentStatusRepository;
    private ReservationQueryService reservationQueryService;
    private DebtCheckService debtCheckService;
    private DoorSummaryQueryServiceImpl service;

    private IotDevice sampleDoor;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(IotDeviceRepository.class);
        residentStatusRepository = mock(ResidentStatusRepository.class);
        reservationQueryService = mock(ReservationQueryService.class);
        debtCheckService = mock(DebtCheckService.class);

        service = new DoorSummaryQueryServiceImpl(
                deviceRepository,
                residentStatusRepository,
                reservationQueryService,
                debtCheckService
        );

        var issue = EdgeGateway.issue(new CreateGatewayCommand("gw-01", 12L, "Torre 1"));
        sampleDoor = new IotDevice(
                new CreateDeviceCommand("door-01", 1L, "Puerta Principal", "Norte", DeviceType.DOOR,
                        Set.of(DeviceCapability.RFID, DeviceCapability.LOCK, DeviceCapability.RGB_LED), 5),
                issue.gateway()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(sampleDoor, "id", 1L);
        sampleDoor.markOnline(Instant.now());
        sampleDoor.setLedColor(new RgbColor("#00FF88"));
    }

    @Test
    void freeAccessClearResidentCanEnterAndReserve() {
        Long userId = 15L;
        AuthenticatedUser user = new AuthenticatedUser(userId, "ana", "ana@edifika.com", Set.of("ROLE_RESIDENT"));

        ResidentStatus status = new ResidentStatus(userId, 12L, Set.of(101L), false, 0, Instant.now());
        when(residentStatusRepository.findByUserId(userId)).thenReturn(Optional.of(status));
        when(deviceRepository.search(null, 12L, DeviceType.DOOR, null)).thenReturn(List.of(sampleDoor));
        when(reservationQueryService.activeReservation(eq(sampleDoor.getId()), any(Instant.class))).thenReturn(Optional.empty());

        List<DoorSummaryResource> result = service.doorsFor(user, Instant.now());

        assertEquals(1, result.size());
        DoorSummaryResource door = result.get(0);
        assertTrue(door.canEnter());
        assertTrue(door.canReserve());
        assertFalse(door.canChangeLed());
        assertNull(door.ledColor());
    }

    @Test
    void delinquentResidentCannotEnterNorReserve() {
        Long userId = 15L;
        AuthenticatedUser user = new AuthenticatedUser(userId, "ana", "ana@edifika.com", Set.of("ROLE_RESIDENT"));

        ResidentStatus status = new ResidentStatus(userId, 12L, Set.of(101L), true, 2, Instant.now());
        when(residentStatusRepository.findByUserId(userId)).thenReturn(Optional.of(status));
        when(deviceRepository.search(null, 12L, DeviceType.DOOR, null)).thenReturn(List.of(sampleDoor));
        when(reservationQueryService.activeReservation(eq(sampleDoor.getId()), any(Instant.class))).thenReturn(Optional.empty());

        List<DoorSummaryResource> result = service.doorsFor(user, Instant.now());

        assertEquals(1, result.size());
        DoorSummaryResource door = result.get(0);
        assertFalse(door.canEnter());
        assertFalse(door.canReserve());
    }

    @Test
    void reservedDoorGuestCanEnterButNotChangeLed() {
        Long guestId = 16L;
        AuthenticatedUser guest = new AuthenticatedUser(guestId, "carlos", "carlos@edifika.com", Set.of("ROLE_RESIDENT"));

        ResidentStatus status = new ResidentStatus(guestId, 12L, Set.of(102L), false, 0, Instant.now());
        when(residentStatusRepository.findByUserId(guestId)).thenReturn(Optional.of(status));
        when(deviceRepository.search(null, 12L, DeviceType.DOOR, null)).thenReturn(List.of(sampleDoor));

        DoorReservation res = DoorReservation.create(new CreateReservationCommand(
                sampleDoor.getId(), 15L, Instant.now().minusSeconds(100), Instant.now().plusSeconds(1000), 15L
        ));
        res.activate();
        res.addAuthorization(guestId, 15L, false);

        when(reservationQueryService.activeReservation(eq(sampleDoor.getId()), any(Instant.class))).thenReturn(Optional.of(res));

        List<DoorSummaryResource> result = service.doorsFor(guest, Instant.now());

        assertEquals(1, result.size());
        DoorSummaryResource door = result.get(0);
        assertTrue(door.canEnter());
        assertFalse(door.canChangeLed());
        assertEquals("#00FF88", door.ledColor()); // Guest sees the color
    }

    @Test
    void holderSeesColorAndCanChangeLed() {
        Long holderId = 15L;
        AuthenticatedUser holder = new AuthenticatedUser(holderId, "ana", "ana@edifika.com", Set.of("ROLE_RESIDENT"));

        ResidentStatus status = new ResidentStatus(holderId, 12L, Set.of(101L), false, 0, Instant.now());
        when(residentStatusRepository.findByUserId(holderId)).thenReturn(Optional.of(status));
        when(deviceRepository.search(null, 12L, DeviceType.DOOR, null)).thenReturn(List.of(sampleDoor));

        DoorReservation res = DoorReservation.create(new CreateReservationCommand(
                sampleDoor.getId(), holderId, Instant.now().minusSeconds(100), Instant.now().plusSeconds(1000), holderId
        ));
        res.activate();

        when(reservationQueryService.activeReservation(eq(sampleDoor.getId()), any(Instant.class))).thenReturn(Optional.of(res));

        List<DoorSummaryResource> result = service.doorsFor(holder, Instant.now());

        assertEquals(1, result.size());
        DoorSummaryResource door = result.get(0);
        assertTrue(door.canEnter());
        assertTrue(door.canChangeLed());
        assertEquals("#00FF88", door.ledColor());
    }
}
