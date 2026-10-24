package com.edifika.access.domain;

import com.edifika.access.access.domain.model.valueobjects.DeviceCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeviceCodeTest {

    @Test
    void acceptsValidCode() {
        DeviceCode code = new DeviceCode("esp32-acc-01");
        assertEquals("esp32-acc-01", code.value());
    }

    @Test
    void rejectsUppercaseAndSpaces() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceCode("ESP32 Door"));
    }

    @Test
    void rejectsNullOrTooShortOrTooLong() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceCode(null));
        assertThrows(IllegalArgumentException.class, () -> new DeviceCode("ab"));
        assertThrows(IllegalArgumentException.class, () -> new DeviceCode("a".repeat(65)));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceCode("esp32_door"));
        assertThrows(IllegalArgumentException.class, () -> new DeviceCode("esp32.door"));
    }
}
