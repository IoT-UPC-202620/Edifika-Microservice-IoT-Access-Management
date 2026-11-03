package com.edifika.access.access.interfaces.rest;

import com.edifika.access.access.application.internal.commandservices.DeviceDiscoveryService;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.DiscoveryStatus;
import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.interfaces.rest.resources.AdoptDeviceResource;
import com.edifika.access.access.interfaces.rest.resources.DeviceResource;
import com.edifika.access.access.interfaces.rest.resources.DiscoveredDeviceResource;
import com.edifika.access.access.interfaces.rest.transform.DeviceResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nodos que los gateways identificaron sin estar registrados: listarlos, identificarlos
 * (parpadeo y beep), adoptarlos completando sus datos o ignorarlos. Solo ADMIN.
 */
@RestController
@RequestMapping(value = "/api/v1/iot/discovered-devices", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Device Discovery", description = "Descubrimiento y adopción de nodos")
public class DiscoveredDeviceController {

    private final DeviceDiscoveryService discoveryService;

    public DiscoveredDeviceController(DeviceDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar nodos descubiertos (por defecto, los pendientes)")
    public ResponseEntity<List<DiscoveredDeviceResource>> list(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String status) {
        DiscoveryStatus wanted = status == null ? null : parseEnum(DiscoveryStatus.class, status, "estado");
        return ResponseEntity.ok(discoveryService.list(buildingId, wanted).stream()
                .map(DiscoveredDeviceResource::from).toList());
    }

    @PostMapping("/{id}/identify")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hacer parpadear y sonar al nodo para reconocer la placa")
    public ResponseEntity<Void> identify(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Integer> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Integer seconds = body == null ? null : body.get("seconds");
        discoveryService.identify(id, seconds, user.requireUserId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/adopt")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adoptar el nodo: crea el dispositivo en el gateway que lo identificó")
    public ResponseEntity<DeviceResource> adopt(@PathVariable Long id, @RequestBody AdoptDeviceResource body) {
        if (body == null) {
            throw new IllegalArgumentException("Faltan los datos del dispositivo");
        }
        Set<DeviceCapability> capabilities = body.capabilities() == null ? Set.of() : body.capabilities().stream()
                .map(c -> parseEnum(DeviceCapability.class, c, "capacidad"))
                .collect(Collectors.toSet());
        var device = discoveryService.adopt(id, new DeviceDiscoveryService.Adoption(
                body.name(),
                body.location(),
                body.deviceType() == null ? null : parseEnum(DeviceType.class, body.deviceType(), "tipo"),
                capabilities,
                body.unlockSeconds() == null ? 0 : body.unlockSeconds()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceResourceFromEntityAssembler.toResourceFromEntity(device));
    }

    @PostMapping("/{id}/ignore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ignorar el nodo: deja de listarse como pendiente")
    public ResponseEntity<DiscoveredDeviceResource> ignore(@PathVariable Long id) {
        return ResponseEntity.ok(DiscoveredDeviceResource.from(discoveryService.ignore(id)));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Valor de " + label + " no válido: " + value);
        }
    }
}
