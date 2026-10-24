package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.exceptions.CredentialNotFoundException;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.commands.ChangeCredentialStatusCommand;
import com.edifika.access.access.domain.model.queries.GetCredentialsQuery;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import com.edifika.access.access.domain.services.CredentialCommandService;
import com.edifika.access.access.domain.services.CredentialQueryService;
import com.edifika.access.access.interfaces.rest.resources.ChangeCredentialStatusResource;
import com.edifika.access.access.interfaces.rest.resources.CreateCredentialResource;
import com.edifika.access.access.interfaces.rest.resources.CredentialResource;
import com.edifika.access.access.interfaces.rest.transform.CreateCredentialCommandFromResourceAssembler;
import com.edifika.access.access.interfaces.rest.transform.CredentialResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/access/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Access Credentials", description = "Gestión de credenciales RFID")
public class AccessCredentialController {

    private final CredentialCommandService commandService;
    private final CredentialQueryService queryService;

    public AccessCredentialController(CredentialCommandService commandService, CredentialQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar nueva credencial RFID")
    public ResponseEntity<CredentialResource> createCredential(@RequestBody CreateCredentialResource resource) {
        var command = CreateCredentialCommandFromResourceAssembler.toCommandFromResource(resource);
        AccessCredential credential = commandService.createCredential(command)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear la credencial"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CredentialResourceFromEntityAssembler.toResourceFromEntity(credential));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar credenciales RFID")
    public ResponseEntity<List<CredentialResource>> getCredentials(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        CredentialStatus cs = status != null ? CredentialStatus.valueOf(status) : null;
        var query = new GetCredentialsQuery(buildingId, userId, cs);
        List<AccessCredential> credentials = queryService.getCredentials(query);
        return ResponseEntity.ok(CredentialResourceFromEntityAssembler.toResourceListFromEntities(credentials));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar estado de credencial RFID")
    public ResponseEntity<CredentialResource> changeStatus(
            @PathVariable Long id,
            @RequestBody ChangeCredentialStatusResource resource) {
        CredentialStatus newStatus = CredentialStatus.valueOf(resource.status());
        var command = new ChangeCredentialStatusCommand(id, newStatus);
        AccessCredential credential = commandService.changeStatus(command)
                .orElseThrow(() -> new CredentialNotFoundException(id));
        return ResponseEntity.ok(CredentialResourceFromEntityAssembler.toResourceFromEntity(credential));
    }
}
