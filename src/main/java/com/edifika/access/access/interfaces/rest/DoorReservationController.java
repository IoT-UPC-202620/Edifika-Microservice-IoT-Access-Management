package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.commands.CancelReservationCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.queries.GetDeviceSlotsQuery;
import com.edifika.access.access.domain.model.queries.GetReservationsByHolderQuery;
import com.edifika.access.access.domain.model.queries.GetReservationsQuery;
import com.edifika.access.access.domain.model.valueobjects.ReservationStatus;
import com.edifika.access.access.domain.services.ReservationCommandService;
import com.edifika.access.access.domain.services.ReservationQueryService;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.interfaces.rest.resources.CreateReservationResource;
import com.edifika.access.access.interfaces.rest.resources.DoorReservationResource;
import com.edifika.access.access.interfaces.rest.resources.ReservationSlotResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Door Reservations", description = "Endpoints para reservas de puertas y gestión de franjas horarias")
public class DoorReservationController {

    private final ReservationCommandService reservationCommandService;
    private final ReservationQueryService reservationQueryService;

    public DoorReservationController(
            ReservationCommandService reservationCommandService,
            ReservationQueryService reservationQueryService) {
        this.reservationCommandService = reservationCommandService;
        this.reservationQueryService = reservationQueryService;
    }

    @GetMapping("/api/v1/iot/devices/{id}/reservations")
    @Operation(summary = "Obtener franjas horarias de reserva de un dispositivo")
    public ResponseEntity<List<ReservationSlotResource>> getSlots(
            @PathVariable("id") Long deviceId,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<ReservationSlotResource> slots = reservationQueryService.getSlots(
                new GetDeviceSlotsQuery(deviceId, from, to),
                user != null ? user.userId() : null
        );
        return ResponseEntity.ok(slots);
    }

    @PostMapping("/api/v1/iot/devices/{id}/reservations")
    @Operation(summary = "Crear reserva para una puerta")
    public ResponseEntity<DoorReservationResource> createReservation(
            @PathVariable("id") Long deviceId,
            @RequestBody CreateReservationResource resource,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long callerId = user != null ? user.requireUserId() : 1L;
        boolean isAdmin = user != null && user.isAdmin();

        Long holderUserId = (isAdmin && resource.holderUserId() != null) ? resource.holderUserId() : callerId;

        DoorReservation created = reservationCommandService.createReservation(new CreateReservationCommand(
                deviceId,
                holderUserId,
                resource.startsAt(),
                resource.endsAt(),
                callerId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(DoorReservationResource.fromEntity(created));
    }

    @GetMapping("/api/v1/access/me/reservations")
    @Operation(summary = "Obtener mis reservas de puerta")
    public ResponseEntity<List<DoorReservationResource>> getMyReservations(
            @RequestParam(name = "status", required = false) ReservationStatus status,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.requireUserId() : 1L;
        List<DoorReservation> list = reservationQueryService.getReservationsByHolder(
                new GetReservationsByHolderQuery(userId, status)
        );
        return ResponseEntity.ok(list.stream().map(DoorReservationResource::fromEntity).toList());
    }

    @GetMapping("/api/v1/access/reservations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar reservas con filtros (solo ADMIN)")
    public ResponseEntity<List<DoorReservationResource>> searchReservations(
            @RequestParam(name = "deviceId", required = false) Long deviceId,
            @RequestParam(name = "status", required = false) ReservationStatus status,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to
    ) {
        List<DoorReservation> list = reservationQueryService.searchReservations(
                new GetReservationsQuery(deviceId, status, from, to)
        );
        return ResponseEntity.ok(list.stream().map(DoorReservationResource::fromEntity).toList());
    }

    @GetMapping("/api/v1/access/reservations/{id}")
    @Operation(summary = "Obtener detalle de una reserva")
    public ResponseEntity<DoorReservationResource> getReservationById(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        DoorReservation r = reservationQueryService.getReservationById(id);
        boolean isAdmin = user != null && user.isAdmin();
        Long userId = user != null ? user.userId() : null;

        if (!isAdmin && (userId == null || !userId.equals(r.getHolderUserId()))) {
            throw new AccessDeniedException("No tiene permisos para ver esta reserva");
        }

        return ResponseEntity.ok(DoorReservationResource.fromEntity(r));
    }

    @DeleteMapping("/api/v1/access/reservations/{id}")
    @Operation(summary = "Cancelar una reserva")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.requireUserId() : 1L;
        boolean isAdmin = user != null && user.isAdmin();

        reservationCommandService.cancelReservation(new CancelReservationCommand(id, userId, isAdmin));
        return ResponseEntity.noContent().build();
    }
}
