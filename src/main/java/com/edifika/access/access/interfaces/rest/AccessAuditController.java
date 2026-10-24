package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.valueobjects.AccessResult;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessAttemptRepository;
import com.edifika.access.access.interfaces.rest.resources.AccessAttemptResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/access", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Access Audit", description = "Endpoints de auditoría de accesos físicos (solo ADMIN)")
public class AccessAuditController {

    private final AccessAttemptRepository attemptRepository;

    public AccessAuditController(AccessAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @GetMapping("/attempts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Auditoría de intentos de acceso físico")
    public ResponseEntity<List<AccessAttemptResource>> getAttempts(
            @RequestParam(name = "deviceId", required = false) Long deviceId,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "result", required = false) AccessResult result,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit
    ) {
        int cappedLimit = Math.min(Math.max(limit, 1), 500);

        List<AccessAttempt> list = attemptRepository.search(
                deviceId,
                userId,
                result,
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
