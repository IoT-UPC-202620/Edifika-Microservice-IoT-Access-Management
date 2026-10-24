package com.edifika.access;

import com.edifika.access.access.application.internal.messaging.ProcessedEventGuard;
import com.edifika.access.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcessedEventGuardIT {

    @Autowired
    private ProcessedEventGuard guard;

    @Test
    void guardReturnsFalseForSecondDelivery() {
        String eventId = "test-event-" + System.currentTimeMillis();
        assertTrue(guard.firstTime(eventId));
        assertFalse(guard.firstTime(eventId));
    }
}
