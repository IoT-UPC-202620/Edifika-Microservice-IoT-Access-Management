package com.edifika.access.domain;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.valueobjects.CredentialOrigin;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import com.edifika.access.access.domain.model.valueobjects.SuspensionReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AccessCredentialTest {

    private AccessCredential activeCredential() {
        return AccessCredential.createCloud("04A1B2C3", 12L, 15L, "Ana Perez", null, null);
    }

    @Test
    void debtSuspensionAndClear() {
        AccessCredential c = activeCredential();
        assertEquals(CredentialStatus.ACTIVE, c.getStatus());
        assertNull(c.getSuspensionReason());

        c.suspendForDebt();
        assertEquals(CredentialStatus.SUSPENDED, c.getStatus());
        assertEquals(SuspensionReason.DELINQUENT, c.getSuspensionReason());
        assertTrue(c.isSuspendedForDebt());

        c.clearDebtSuspension();
        assertEquals(CredentialStatus.ACTIVE, c.getStatus());
        assertNull(c.getSuspensionReason());
    }

    @Test
    void debtSuspensionOnlyClearsItself() {
        AccessCredential c = activeCredential();
        Instant now = Instant.now();
        c.changeStatus(CredentialStatus.SUSPENDED, now);
        assertNull(c.getSuspensionReason());

        c.clearDebtSuspension();
        assertEquals(CredentialStatus.SUSPENDED, c.getStatus());
    }

    @Test
    void lostMapsToRevokedForEdge() {
        AccessCredential c = activeCredential();
        c.changeStatus(CredentialStatus.LOST, Instant.now());
        assertEquals("REVOKED", c.edgeStatus());
    }

    @Test
    void usableAtValidatesWindow() {
        Instant now = Instant.now();
        AccessCredential c = AccessCredential.createCloud(
                "04A1B2C3",
                12L,
                15L,
                "Ana Perez",
                now.minusSeconds(3600),
                now.plusSeconds(3600)
        );
        assertTrue(c.isUsableAt(now));
        assertFalse(c.isUsableAt(now.minusSeconds(7200)));
        assertFalse(c.isUsableAt(now.plusSeconds(7200)));
    }

    @Test
    void edgeCreatedCredentialProperties() {
        AccessCredential c = AccessCredential.createFromEdge("04A1B2C3", "guardia-1", CredentialStatus.ACTIVE, 12L);
        assertEquals(CredentialOrigin.EDGE, c.getOrigin());
        assertNull(c.getUserId());
        assertEquals("guardia-1", c.residentRef());
        assertEquals("guardia-1", c.getEdgeResidentRef());
    }
}
