package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.domain.exceptions.DeviceNotFoundException;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.DecommissionDeviceCommand;
import com.edifika.access.access.domain.model.commands.SetDeviceMaintenanceCommand;
import com.edifika.access.access.domain.model.queries.GetDeviceByIdQuery;
import com.edifika.access.access.domain.model.queries.GetDevicesQuery;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.services.DeviceCommandService;
import com.edifika.access.access.domain.services.DeviceQueryService;
import com.edifika.access.access.interfaces.rest.resources.CreateDeviceResource;
import com.edifika.access.access.interfaces.rest.resources.DeviceMaintenanceResource;
import com.edifika.access.access.interfaces.rest.resources.DeviceResource;
import com.edifika.access.access.interfaces.rest.resources.UpdateDeviceResource;
import com.edifika.access.access.interfaces.rest.transform.CreateDeviceCommandFromResourceAssembler;
import com.edifika.access.access.interfaces.rest.transform.DeviceResourceFromEntityAssembler;
import com.edifika.access.access.interfaces.rest.transform.UpdateDeviceCommandFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/iot/devices", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "IoT Devices", description = "Endpoints para la gestión de dispositivos ESP32")
public class IotDeviceController {

    private final DeviceCommandService commandService;
    private final DeviceQueryService queryService;

    public IotDeviceController(DeviceCommandService commandService, DeviceQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar nuevo dispositivo ESP32")
    public ResponseEntity<DeviceResource> createDevice(@RequestBody CreateDeviceResource resource) {
        var command = CreateDeviceCommandFromResourceAssembler.toCommandFromResource(resource);
        IotDevice device = commandService.createDevice(command)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear el dispositivo"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DeviceResourceFromEntityAssembler.toResourceFromEntity(device));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar dispositivos ESP32")
    public ResponseEntity<List<DeviceResource>> getDevices(
            @RequestParam(required = false) Long gatewayId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String status) {
        DeviceType dt = deviceType != null ? DeviceType.valueOf(deviceType) : null;
        DeviceStatus ds = status != null ? DeviceStatus.valueOf(status) : null;
        var query = new GetDevicesQuery(gatewayId, buildingId, dt, ds);
        List<IotDevice> devices = queryService.getDevices(query);
        return ResponseEntity.ok(DeviceResourceFromEntityAssembler.toResourceListFromEntities(devices));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener dispositivo por ID")
    public ResponseEntity<DeviceResource> getDeviceById(@PathVariable Long id) {
        var query = new GetDeviceByIdQuery(id);
        IotDevice device = queryService.getDeviceById(query)
                .orElseThrow(() -> new DeviceNotFoundException(id));
        return ResponseEntity.ok(DeviceResourceFromEntityAssembler.toResourceFromEntity(device));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar dispositivo ESP32")
    public ResponseEntity<DeviceResource> updateDevice(
            @PathVariable Long id,
            @RequestBody UpdateDeviceResource resource) {
        var command = UpdateDeviceCommandFromResourceAssembler.toCommandFromResource(id, resource);
        IotDevice device = commandService.updateDevice(command)
                .orElseThrow(() -> new DeviceNotFoundException(id));
        return ResponseEntity.ok(DeviceResourceFromEntityAssembler.toResourceFromEntity(device));
    }

    @PutMapping("/{id}/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar o desactivar modo mantenimiento")
    public ResponseEntity<DeviceResource> setMaintenance(
            @PathVariable Long id,
            @RequestBody DeviceMaintenanceResource resource) {
        var command = new SetDeviceMaintenanceCommand(id, resource.enabled());
        IotDevice device = commandService.setMaintenance(command)
                .orElseThrow(() -> new DeviceNotFoundException(id));
        return ResponseEntity.ok(DeviceResourceFromEntityAssembler.toResourceFromEntity(device));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dar de baja dispositivo ESP32")
    public ResponseEntity<Void> decommissionDevice(@PathVariable Long id) {
        commandService.decommissionDevice(new DecommissionDeviceCommand(id));
        return ResponseEntity.noContent().build();
    }
}
