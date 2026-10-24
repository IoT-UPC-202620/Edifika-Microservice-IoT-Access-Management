package com.edifika.access.domain;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.GatewayStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EdgeGatewayTest {

    private CreateGatewayCommand cmd() {
        return new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A");
    }

    @Test
    void apiKeyIsHashedAndRotates() {
        var issue = EdgeGateway.issue(cmd());
        assertNotNull(issue.gateway());
        assertNotNull(issue.plainApiKey());
        assertTrue(issue.plainApiKey().startsWith("egk_"));
        assertEquals(48, issue.plainApiKey().length());

        assertTrue(issue.gateway().apiKeyMatches(issue.plainApiKey()));
        assertFalse(issue.gateway().apiKeyMatches("wrong-key"));

        var old = issue.plainApiKey();
        var neu = issue.gateway().rotateApiKey();
        assertNotEquals(old, neu);
        assertFalse(issue.gateway().apiKeyMatches(old));
        assertTrue(issue.gateway().apiKeyMatches(neu));
    }

    @Test
    void bumpSyncVersionIncrements() {
        var gateway = EdgeGateway.issue(cmd()).gateway();
        assertEquals(1L, gateway.getSyncVersion());
        assertEquals(2L, gateway.bumpSyncVersion());
        assertEquals(2L, gateway.getSyncVersion());
    }

    @Test
    void markSyncedSetsOnlineAndUpdatesTimestamp() {
        var gateway = EdgeGateway.issue(cmd()).gateway();
        assertEquals(GatewayStatus.PROVISIONED, gateway.getStatus());
        assertNull(gateway.getLastSyncAt());

        Instant now = Instant.now();
        gateway.markSynced(now);

        assertEquals(GatewayStatus.ONLINE, gateway.getStatus());
        assertEquals(now, gateway.getLastSyncAt());
    }
}
