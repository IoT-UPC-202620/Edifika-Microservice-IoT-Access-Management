package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.model.commands.SetLedColorCommand;
import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.services.DeviceControlCommandService;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.interfaces.rest.resources.DeviceCommandResource;
import com.edifika.access.access.interfaces.rest.resources.SetLedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/api/v1/iot", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Device Control", description = "Endpoints para control de dispositivos y órdenes de LED RGB")
public class DeviceControlController {

    private final DeviceControlCommandService deviceControlCommandService;

    public DeviceControlController(DeviceControlCommandService deviceControlCommandService) {
        this.deviceControlCommandService = deviceControlCommandService;
    }

    @PutMapping("/devices/{id}/led")
    @Operation(summary = "Enviar orden de color para LED RGB de un dispositivo")
    public ResponseEntity<DeviceCommandResource> setLed(
            @PathVariable("id") Long deviceId,
            @RequestBody SetLedResource resource,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.requireUserId() : 1L;
        boolean isAdmin = user != null && user.isAdmin();

        DeviceCommand cmd = deviceControlCommandService.issueSetLedColor(new SetLedColorCommand(
                deviceId,
                resource.color(),
                userId,
                isAdmin
        ));

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DeviceCommandResource.fromEntity(cmd));
    }

    @GetMapping("/device-commands/{commandId}")
    @Operation(summary = "Consultar estado de un comando enviado al dispositivo")
    public ResponseEntity<DeviceCommandResource> getCommand(
            @PathVariable("commandId") String commandId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        DeviceCommand cmd = deviceControlCommandService.getCommand(commandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comando no encontrado"));

        Long userId = user != null ? user.userId() : null;
        boolean isAdmin = user != null && user.isAdmin();

        if (!isAdmin && (userId == null || !userId.equals(cmd.getRequestedBy()))) {
            throw new AccessDeniedException("No tiene permisos para ver este comando");
        }

        return ResponseEntity.ok(DeviceCommandResource.fromEntity(cmd));
    }
}
