package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.application.internal.commandservices.CardEnrollmentService;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.interfaces.rest.resources.CardEnrollmentResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Modo registro de tarjetas en un lector. Solo ADMIN: el administrador elige el
 * lector, captura la tarjeta y luego la asocia con POST /api/v1/access/credentials.
 */
@RestController
@RequestMapping(value = "/api/v1/iot/devices/{id}/enrollment", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Card Enrollment", description = "Modo registro de tarjetas RFID en un lector")
public class CardEnrollmentController {

    private final CardEnrollmentService enrollmentService;

    public CardEnrollmentController(CardEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Poner el lector en modo registro (60 s)")
    public ResponseEntity<CardEnrollmentResource> start(
            @PathVariable("id") Long deviceId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        var enrollment = enrollmentService.start(deviceId, user.requireUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CardEnrollmentResource.from(enrollment, null));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar la sesión de registro del lector (tarjeta capturada)")
    public ResponseEntity<CardEnrollmentResource> current(@PathVariable("id") Long deviceId) {
        return enrollmentService.current(deviceId)
                .map(view -> ResponseEntity.ok(CardEnrollmentResource.from(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Salir del modo registro")
    public ResponseEntity<Void> cancel(
            @PathVariable("id") Long deviceId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        enrollmentService.cancel(deviceId, user.requireUserId());
        return ResponseEntity.noContent().build();
    }
}
