package com.edifika.access.domain;

import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RfidUidTest {

    @Test
    void uidIsNormalized() {
        assertEquals("04A1B2C3", new RfidUid("04 a1:b2-c3").value());
    }

    @Test
    void uidMasked() {
        assertEquals("****B2C3", new RfidUid("04A1B2C3").masked());
    }

    @Test
    void rejectsInvalidUids() {
        assertThrows(IllegalArgumentException.class, () -> new RfidUid(null));
        assertThrows(IllegalArgumentException.class, () -> new RfidUid("1234567")); // 7 chars, min 8
        assertThrows(IllegalArgumentException.class, () -> new RfidUid("04A1B2C3GG")); // G is invalid hex
        assertThrows(IllegalArgumentException.class, () -> new RfidUid("0".repeat(21))); // 21 chars, max 20
    }
}
