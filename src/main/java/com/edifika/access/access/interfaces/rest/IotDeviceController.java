package com.edifika.access.access.interfaces.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador provisional de dispositivos IoT (esqueleto).
 * Será completado en Task 3.
 */
@RestController
@RequestMapping(value = "/api/v1/iot/devices", produces = "application/json")
@Tag(name = "IoT Devices", description = "Endpoints para la gestión de dispositivos ESP32")
public class IotDeviceController {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Object>> getAll() {
        return ResponseEntity.ok(List.of());
    }
}
