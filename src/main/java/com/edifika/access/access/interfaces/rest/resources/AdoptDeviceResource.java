package com.edifika.access.access.interfaces.rest.resources;

import java.util.Set;

/** Datos que completa el administrador al adoptar un nodo descubierto. */
public record AdoptDeviceResource(
        String name,
        String location,
        String deviceType,
        Set<String> capabilities,
        Integer unlockSeconds
) {
}
