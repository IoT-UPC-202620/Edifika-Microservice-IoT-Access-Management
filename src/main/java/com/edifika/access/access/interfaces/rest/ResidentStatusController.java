package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.application.internal.debts.DebtCheckService;
import com.edifika.access.access.domain.model.entities.ResidentStatus;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ResidentStatusRepository;
import com.edifika.access.access.interfaces.rest.resources.ResidentStatusResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/access/residents", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Resident Status", description = "Endpoints de estado de residente y verificación de morosidad")
public class ResidentStatusController {

    private final DebtCheckService debtCheckService;
    private final ResidentStatusRepository residentStatusRepository;

    public ResidentStatusController(
            DebtCheckService debtCheckService,
            ResidentStatusRepository residentStatusRepository) {
        this.debtCheckService = debtCheckService;
        this.residentStatusRepository = residentStatusRepository;
    }

    @GetMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN') or #userId == principal.userId")
    @Operation(summary = "Obtener estado de morosidad de un residente")
    public ResponseEntity<ResidentStatusResource> getStatus(@PathVariable("userId") Long userId) {
        return residentStatusRepository.findByUserId(userId)
                .map(ResidentStatusResource::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}/debt-check")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Forzar verificación de morosidad de un residente")
    public ResponseEntity<ResidentStatusResource> debtCheck(@PathVariable("userId") Long userId) {
        ResidentStatus status = debtCheckService.refreshUser(userId);
        return ResponseEntity.ok(ResidentStatusResource.fromEntity(status));
    }
}
