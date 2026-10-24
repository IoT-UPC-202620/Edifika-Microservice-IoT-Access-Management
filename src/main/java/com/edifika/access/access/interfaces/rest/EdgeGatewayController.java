package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.exceptions.GatewayNotFoundException;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.commands.RotateGatewayApiKeyCommand;
import com.edifika.access.access.domain.model.queries.GetAllGatewaysQuery;
import com.edifika.access.access.domain.model.queries.GetGatewayByIdQuery;
import com.edifika.access.access.domain.services.GatewayCommandService;
import com.edifika.access.access.domain.services.GatewayQueryService;
import com.edifika.access.access.interfaces.rest.resources.CreateGatewayResource;
import com.edifika.access.access.interfaces.rest.resources.GatewayCredentialsResource;
import com.edifika.access.access.interfaces.rest.resources.GatewayResource;
import com.edifika.access.access.interfaces.rest.transform.CreateGatewayCommandFromResourceAssembler;
import com.edifika.access.access.interfaces.rest.transform.GatewayCredentialsResourceFromIssueAssembler;
import com.edifika.access.access.interfaces.rest.transform.GatewayResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/iot/gateways", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Edge Gateways", description = "Gestión de Edge Gateways")
public class EdgeGatewayController {

    private final GatewayCommandService commandService;
    private final GatewayQueryService queryService;

    public EdgeGatewayController(GatewayCommandService commandService, GatewayQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear nuevo Edge Gateway")
    public ResponseEntity<GatewayCredentialsResource> createGateway(@RequestBody CreateGatewayResource resource) {
        var command = CreateGatewayCommandFromResourceAssembler.toCommandFromResource(resource);
        var issue = commandService.createGateway(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GatewayCredentialsResourceFromIssueAssembler.toResourceFromIssue(issue));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar Edge Gateways")
    public ResponseEntity<List<GatewayResource>> getGateways(@RequestParam(required = false) Long buildingId) {
        var query = new GetAllGatewaysQuery(buildingId);
        List<EdgeGateway> gateways = queryService.getAllGateways(query);
        return ResponseEntity.ok(GatewayResourceFromEntityAssembler.toResourceListFromEntities(gateways));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener Edge Gateway por ID")
    public ResponseEntity<GatewayResource> getGatewayById(@PathVariable Long id) {
        var query = new GetGatewayByIdQuery(id);
        EdgeGateway gateway = queryService.getGatewayById(query)
                .orElseThrow(() -> new GatewayNotFoundException(id));
        return ResponseEntity.ok(GatewayResourceFromEntityAssembler.toResourceFromEntity(gateway));
    }

    @PostMapping("/{id}/api-key")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rotar API Key del Edge Gateway")
    public ResponseEntity<GatewayCredentialsResource> rotateApiKey(@PathVariable Long id) {
        var command = new RotateGatewayApiKeyCommand(id);
        var issue = commandService.rotateApiKey(command);
        return ResponseEntity.ok(GatewayCredentialsResourceFromIssueAssembler.toResourceFromIssue(issue));
    }
}
