package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.access.domain.services.DoorSummaryQueryService;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessAttemptRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.interfaces.rest.resources.AccessAttemptResource;
import com.edifika.access.access.interfaces.rest.resources.CredentialResource;
import com.edifika.access.access.interfaces.rest.resources.DoorSummaryResource;
import com.edifika.access.access.interfaces.rest.transform.CredentialResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/access/me", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Resident Views", description = "Vistas del residente para puertas, credenciales e intentos de acceso")
public class MyAccessController {

    private final DoorSummaryQueryService doorSummaryQueryService;
    private final AccessCredentialRepository credentialRepository;
    private final AccessAttemptRepository attemptRepository;

    public MyAccessController(
            DoorSummaryQueryService doorSummaryQueryService,
            AccessCredentialRepository credentialRepository,
            AccessAttemptRepository attemptRepository) {
        this.doorSummaryQueryService = doorSummaryQueryService;
        this.credentialRepository = credentialRepository;
        this.attemptRepository = attemptRepository;
    }

    @GetMapping("/doors")
    @Operation(summary = "Resumen de puertas para el residente autenticado")
    public ResponseEntity<List<DoorSummaryResource>> getDoors(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<DoorSummaryResource> doors = doorSummaryQueryService.doorsFor(user, Instant.now());
        return ResponseEntity.ok(doors);
    }

    @GetMapping("/credentials")
    @Operation(summary = "Listar mis credenciales RFID (con UID enmascarado)")
    public ResponseEntity<List<CredentialResource>> getMyCredentials(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.requireUserId() : 1L;
        List<AccessCredential> credentials = credentialRepository.findAllByUserId(userId);
        List<CredentialResource> resources = CredentialResourceFromEntityAssembler.toResourceListFromEntities(credentials, true);
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/attempts")
    @Operation(summary = "Consultar mis intentos de acceso")
    public ResponseEntity<List<AccessAttemptResource>> getMyAttempts(
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.requireUserId() : 1L;
        int cappedLimit = Math.min(Math.max(limit, 1), 50);

        List<AccessAttempt> list = attemptRepository.search(
                null,
                userId,
                null,
                from,
                to,
                PageRequest.of(0, cappedLimit)
        );

        List<AccessAttemptResource> resources = list.stream()
                .map(a -> AccessAttemptResource.fromEntity(a, null))
                .toList();

        return ResponseEntity.ok(resources);
    }
}
