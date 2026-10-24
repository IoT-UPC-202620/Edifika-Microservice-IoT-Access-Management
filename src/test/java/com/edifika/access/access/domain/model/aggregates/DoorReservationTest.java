package com.edifika.access.access.domain.model.aggregates;

import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.entities.DoorAuthorization;
import com.edifika.access.access.domain.model.valueobjects.AuthorizationStatus;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DoorReservationTest {

    @Test
    void createReservationInitializesScheduled() {
        Instant now = Instant.now();
        DoorReservation reservation = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.plusSeconds(3600), now.plusSeconds(7200), 15L
        ));

        assertEquals(1L, reservation.getDeviceId());
        assertEquals(15L, reservation.getHolderUserId());
        assertEquals(ReservationStatus.SCHEDULED, reservation.getStatus());
        assertTrue(reservation.getAuthorizations().isEmpty());
    }

    @Test
    void holderCannotBeAddedAsGuest() {
        Instant now = Instant.now();
        DoorReservation reservation = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.plusSeconds(3600), now.plusSeconds(7200), 15L
        ));

        assertThrows(IllegalArgumentException.class, () ->
                reservation.addAuthorization(15L, 15L, false)
        );
    }

    @Test
    void onlyHolderOrAdminManagesGuests() {
        Instant now = Instant.now();
        DoorReservation reservation = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.plusSeconds(3600), now.plusSeconds(7200), 15L
        ));

        // Other resident (userId=99) cannot add guest
        assertThrows(AccessDeniedException.class, () ->
                reservation.addAuthorization(16L, 99L, false)
        );

        // Holder (userId=15) can add guest
        DoorAuthorization auth = reservation.addAuthorization(16L, 15L, false);
        assertNotNull(auth);
        assertEquals(16L, auth.getUserId());
        assertEquals(AuthorizationStatus.ACTIVE, auth.getStatus());

        // Admin can add guest
        DoorAuthorization auth2 = reservation.addAuthorization(17L, 1L, true);
        assertNotNull(auth2);

        // Other resident cannot remove guest
        assertThrows(AccessDeniedException.class, () ->
                reservation.removeAuthorization(16L, 99L, false)
        );

        // Holder can remove guest
        reservation.removeAuthorization(16L, 15L, false);
        assertFalse(auth.isActive());
    }

    @Test
    void cannotAddGuestToCancelledOrEndedReservation() {
        Instant now = Instant.now();
        DoorReservation reservation = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.plusSeconds(3600), now.plusSeconds(7200), 15L
        ));
        reservation.cancel(15L, false);

        assertThrows(IllegalStateException.class, () ->
                reservation.addAuthorization(16L, 15L, false)
        );
    }

    @Test
    void cancellingActiveReservationTransitionsToCancelled() {
        Instant now = Instant.now();
        DoorReservation reservation = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.plusSeconds(3600), now.plusSeconds(7200), 15L
        ));
        reservation.activate();
        assertEquals(ReservationStatus.ACTIVE, reservation.getStatus());

        reservation.cancel(15L, false);
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
    }

    @Test
    void nonHolderNonAdminCannotCancelReservation() {
        Instant now = Instant.now();
        DoorReservation reservation = DoorReservation.create(new CreateReservationCommand(
                1L, 15L, now.plusSeconds(3600), now.plusSeconds(7200), 15L
        ));

        assertThrows(AccessDeniedException.class, () ->
                reservation.cancel(99L, false)
        );
    }
}
