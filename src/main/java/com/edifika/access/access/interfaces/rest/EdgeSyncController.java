package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.application.internal.edge.EdgeSnapshotService;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.infrastructure.authorization.sfs.EdgeGatewayAuthenticationFilter;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.interfaces.rest.resources.edge.EdgeSyncResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/edge")
@Tag(name = "Edge Sync", description = "Endpoints para sincronización de gateways edge ESP32")
public class EdgeSyncController {

    private final EdgeSnapshotService snapshotService;
    private final EdgeGatewayRepository gatewayRepository;

    public EdgeSyncController(EdgeSnapshotService snapshotService, EdgeGatewayRepository gatewayRepository) {
        this.snapshotService = snapshotService;
        this.gatewayRepository = gatewayRepository;
    }

    @GetMapping("/sync")
    @Transactional
    @Operation(summary = "Obtener snapshot de sincronización del gateway")
    public ResponseEntity<EdgeSyncResource> sync(
            @RequestParam(name = "since", required = false) Long since,
            HttpServletRequest request
    ) {
        EdgeGateway gatewayAttr = (EdgeGateway) request.getAttribute(EdgeGatewayAuthenticationFilter.GATEWAY_REQUEST_ATTR);
        if (gatewayAttr == null) {
            return ResponseEntity.status(401).build();
        }

        EdgeGateway gateway = gatewayRepository.findById(gatewayAttr.getId()).orElse(gatewayAttr);
        EdgeSyncResource snapshot = snapshotService.snapshot(gateway, since);
        gateway.markSynced(Instant.now());
        gatewayRepository.save(gateway);

        return ResponseEntity.ok(snapshot);
    }
}
