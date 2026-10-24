package com.edifika.access.domain;

import com.edifika.access.access.domain.model.valueobjects.RgbColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RgbColorTest {

    @Test
    void normalizesToUppercase() {
        assertEquals("#00FF88", new RgbColor("#00ff88").value());
    }

    @Test
    void rejectsShortHex() {
        assertThrows(IllegalArgumentException.class, () -> new RgbColor("#0F8"));
    }

    @Test
    void rejectsNullOrInvalidHex() {
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(null));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor("00FF88"));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor("#GGFFFF"));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor("#00FF8800"));
    }
}
