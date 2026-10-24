package com.edifika.access;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.entities.ResidentStatus;
import com.edifika.access.access.domain.model.valueobjects.*;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.*;
import com.edifika.access.support.ContractAssertions;
import com.edifika.access.support.JwtTestTokens;
import com.edifika.access.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MyAccessControllerIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private AccessCredentialRepository credentialRepository;

    @Autowired
    private AccessAttemptRepository attemptRepository;

    @Autowired
    private ResidentStatusRepository residentStatusRepository;

    @Autowired
    private IotDeviceRepository deviceRepository;

    @Autowired
    private EdgeGatewayRepository gatewayRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long sampleDeviceId;

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAll();
        credentialRepository.deleteAll();
        residentStatusRepository.deleteAll();
        deviceRepository.deleteAll();
        gatewayRepository.deleteAll();

        var issue = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A"));
        EdgeGateway gateway = gatewayRepository.save(issue.gateway());

        IotDevice device = new IotDevice(
                new CreateDeviceCommand("esp32-door-my", gateway.getId(), "Puerta Entrada", "Lobby", DeviceType.DOOR,
                        Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                gateway
        );
        device.markOnline(Instant.now());
        IotDevice saved = deviceRepository.save(device);
        sampleDeviceId = saved.getId();

        ResidentStatus status = new ResidentStatus(15L, 12L, Set.of(101L), false, 0, Instant.now());
        residentStatusRepository.save(status);
    }

    private HttpHeaders residentHeaders(long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.resident(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.admin());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void myCredentialsAreMasked() throws Exception {
        AccessCredential cred = AccessCredential.createCloud(
                "04A1B2C3D4", 12L, 15L, "Ana Perez", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(86400)
        );
        credentialRepository.save(cred);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/access/me/credentials",
                HttpMethod.GET,
                new HttpEntity<>(residentHeaders(15L)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var array = objectMapper.readTree(response.getBody());
        assertEquals(1, array.size());
        assertEquals("****B2C3D4", array.get(0).get("rfidUid").asText());
    }

    @Test
    void attemptsLimitIsCapped() throws Exception {
        // Seed 60 attempts for user 15
        for (int i = 0; i < 60; i++) {
            AccessAttempt att = new AccessAttempt(
                    "att-" + i,
                    sampleDeviceId,
                    "esp32-door-my",
                    12L,
                    "04A1B2C3",
                    15L,
                    "Ana Perez",
                    AccessResult.GRANTED,
                    AccessReason.GRANTED,
                    AccessMode.FREE_ACCESS,
                    Instant.now().minusSeconds(i * 10),
                    "evt-" + i
            );
            attemptRepository.save(att);
        }

        // Request with limit 1000 -> should cap to 50 for resident
        ResponseEntity<String> response = http.exchange(
                "/api/v1/access/me/attempts?limit=1000",
                HttpMethod.GET,
                new HttpEntity<>(residentHeaders(15L)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var array = objectMapper.readTree(response.getBody());
        assertEquals(50, array.size(), "Limit should be capped at 50 for resident");
    }

    @Test
    void responsesMatchContract() throws Exception {
        // 1. GET /api/v1/access/me/doors -> DoorSummaryResource
        ResponseEntity<String> doorsRes = http.exchange(
                "/api/v1/access/me/doors",
                HttpMethod.GET,
                new HttpEntity<>(residentHeaders(15L)),
                String.class
        );
        assertEquals(HttpStatus.OK, doorsRes.getStatusCode());
        var doorsJson = objectMapper.readTree(doorsRes.getBody());
        assertTrue(doorsJson.isArray() && !doorsJson.isEmpty());
        ContractAssertions.assertMatches("contracts/rest/access/DoorSummaryResource.schema.json", doorsJson.get(0).toString());

        // 2. Seed attempt and test GET /api/v1/access/attempts (ADMIN) -> AccessAttemptResource
        AccessAttempt att = new AccessAttempt(
                "att-audit-1",
                sampleDeviceId,
                "esp32-door-my",
                12L,
                "04A1B2C3",
                15L,
                "Ana Perez",
                AccessResult.GRANTED,
                AccessReason.GRANTED,
                AccessMode.FREE_ACCESS,
                Instant.now(),
                "evt-audit-1"
        );
        attemptRepository.save(att);

        ResponseEntity<String> auditRes = http.exchange(
                "/api/v1/access/attempts",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                String.class
        );
        assertEquals(HttpStatus.OK, auditRes.getStatusCode());
        var auditJson = objectMapper.readTree(auditRes.getBody());
        assertTrue(auditJson.isArray() && !auditJson.isEmpty());
        ContractAssertions.assertMatches("contracts/rest/access/AccessAttemptResource.schema.json", auditJson.get(0).toString());
    }
}
