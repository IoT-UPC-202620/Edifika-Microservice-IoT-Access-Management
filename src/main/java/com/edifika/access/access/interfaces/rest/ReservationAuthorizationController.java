package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.commands.AddAuthorizationCommand;
import com.edifika.access.access.domain.model.commands.RemoveAuthorizationCommand;
import com.edifika.access.access.domain.model.entities.DoorAuthorization;
import com.edifika.access.access.domain.services.ReservationCommandService;
import com.edifika.access.access.domain.services.ReservationQueryService;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.interfaces.rest.resources.AddAuthorizationResource;
import com.edifika.access.access.interfaces.rest.resources.AuthorizationResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/access/reservations/{id}/authorizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Reservation Authorizations", description = "Endpoints para autorizaciones de invitados en reservas de puerta")
public class ReservationAuthorizationController {

    private final ReservationCommandService reservationCommandService;
    private final ReservationQueryService reservationQueryService;

    public ReservationAuthorizationController(
            ReservationCommandService reservationCommandService,
            ReservationQueryService reservationQueryService) {
        this.reservationCommandService = reservationCommandService;
        this.reservationQueryService = reservationQueryService;
    }

    @PostMapping
    @Operation(summary = "Autorizar a un residente invitado en la reserva")
    public ResponseEntity<AuthorizationResource> addAuthorization(
            @PathVariable("id") Long id,
            @RequestBody AddAuthorizationResource resource,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long callerId = user != null ? user.requireUserId() : 1L;
        boolean isAdmin = user != null && user.isAdmin();

        DoorAuthorization auth = reservationCommandService.addAuthorization(new AddAuthorizationCommand(
                id,
                resource.userId(),
                callerId,
                isAdmin
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthorizationResource.fromEntity(id, auth));
    }

    @GetMapping
    @Operation(summary = "Listar autorizados de una reserva")
    public ResponseEntity<List<AuthorizationResource>> getAuthorizations(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        DoorReservation r = reservationQueryService.getReservationById(id);
        boolean isAdmin = user != null && user.isAdmin();
        Long callerId = user != null ? user.userId() : null;

        if (!isAdmin && (callerId == null || !callerId.equals(r.getHolderUserId()))) {
            throw new AccessDeniedException("No tiene permisos para ver los autorizados de esta reserva");
        }

        List<AuthorizationResource> list = r.getAuthorizations().stream()
                .map(a -> AuthorizationResource.fromEntity(id, a))
                .toList();

        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Revocar autorización de un residente invitado")
    public ResponseEntity<Void> removeAuthorization(
            @PathVariable("id") Long id,
            @PathVariable("userId") Long userId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long callerId = user != null ? user.requireUserId() : 1L;
        boolean isAdmin = user != null && user.isAdmin();

        reservationCommandService.removeAuthorization(new RemoveAuthorizationCommand(
                id,
                userId,
                callerId,
                isAdmin
        ));

        return ResponseEntity.noContent().build();
    }
}
