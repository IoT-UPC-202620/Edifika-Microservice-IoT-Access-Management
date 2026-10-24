package com.edifika.access;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.*;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.support.ContractAssertions;
import com.edifika.access.support.JwtTestTokens;
import com.edifika.access.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EdgeSyncControllerIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private EdgeGatewayRepository gatewayRepository;

    @Autowired
    private IotDeviceRepository deviceRepository;

    @Autowired
    private AccessCredentialRepository credentialRepository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private EdgeGateway sampleGateway;
    private String plainApiKey;

    @BeforeEach
    void setup() {
        credentialRepository.deleteAll();
        deviceRepository.deleteAll();
        gatewayRepository.deleteAll();

        var issue = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A"));
        sampleGateway = gatewayRepository.save(issue.gateway());
        plainApiKey = issue.plainApiKey();
    }

    private HttpHeaders edgeHeaders(String gatewayCode, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Id", gatewayCode);
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.admin());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void sinceEqualToVersionReturnsEmptyIncrement() throws Exception {
        ResponseEntity<String> response = http.exchange(
                "/api/v1/edge/sync?since=" + sampleGateway.getSyncVersion(),
                HttpMethod.GET,
                new HttpEntity<>(edgeHeaders(sampleGateway.getGatewayCode(), plainApiKey)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var root = objectMapper.readTree(response.getBody());
        assertEquals(sampleGateway.getSyncVersion(), root.get("version").asLong());
        assertFalse(root.get("full").asBoolean());
        assertTrue(root.get("credentials").isEmpty());
        assertTrue(root.get("devices").isEmpty());
    }

    @Test
    void otherwiseReturnsFullSnapshotOfGatewayBuilding() throws Exception {
        // Create an active credential in building 12
        AccessCredential credential = AccessCredential.createCloud(
                "04A1B2C3D4E5",
                12L,
                42L,
                "Juan Pérez",
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86400)
        );
        credentialRepository.save(credential);

        // Create a device on this gateway
        IotDevice device = new IotDevice(
                new CreateDeviceCommand("esp32-door-01", sampleGateway.getId(), "Puerta Principal", "Acceso Norte", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                sampleGateway
        );
        deviceRepository.save(device);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/edge/sync?since=0",
                HttpMethod.GET,
                new HttpEntity<>(edgeHeaders(sampleGateway.getGatewayCode(), plainApiKey)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var root = objectMapper.readTree(response.getBody());
        assertTrue(root.get("full").asBoolean());
        assertEquals(1, root.get("credentials").size());
        assertEquals("04A1B2C3D4E5", root.get("credentials").get(0).get("uid").asText());
        assertEquals(1, root.get("devices").size());
        assertEquals("esp32-door-01", root.get("devices").get(0).get("deviceCode").asText());
    }

    @Test
    void decommissionedDevicesAreExcluded() throws Exception {
        IotDevice activeDev = new IotDevice(
                new CreateDeviceCommand("esp32-door-act", sampleGateway.getId(), "Puerta Activa", "Lobby", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                sampleGateway
        );
        deviceRepository.save(activeDev);

        IotDevice decommDev = new IotDevice(
                new CreateDeviceCommand("esp32-door-dec", sampleGateway.getId(), "Puerta Antigua", "Sotano", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                sampleGateway
        );
        decommDev.decommission();
        deviceRepository.save(decommDev);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/edge/sync",
                HttpMethod.GET,
                new HttpEntity<>(edgeHeaders(sampleGateway.getGatewayCode(), plainApiKey)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var root = objectMapper.readTree(response.getBody());
        assertEquals(1, root.get("devices").size());
        assertEquals("esp32-door-act", root.get("devices").get(0).get("deviceCode").asText());
    }

    @Test
    void syncResponseMatchesContract() throws Exception {
        AccessCredential credential = AccessCredential.createCloud(
                "04A1B2C3D4E5",
                12L,
                42L,
                "Juan Pérez",
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(86400)
        );
        credentialRepository.save(credential);

        IotDevice device = new IotDevice(
                new CreateDeviceCommand("esp32-door-01", sampleGateway.getId(), "Puerta Principal", "Acceso Norte", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                sampleGateway
        );
        deviceRepository.save(device);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/edge/sync",
                HttpMethod.GET,
                new HttpEntity<>(edgeHeaders(sampleGateway.getGatewayCode(), plainApiKey)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ContractAssertions.assertMatches("contracts/sync.schema.json", response.getBody());
    }

    @Test
    void rotatedKeyIsRejected() {
        sampleGateway.rotateApiKey();
        gatewayRepository.save(sampleGateway);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/edge/sync",
                HttpMethod.GET,
                new HttpEntity<>(edgeHeaders(sampleGateway.getGatewayCode(), plainApiKey)),
                String.class
        );
        if (response.getStatusCode().is5xxServerError()) {
            System.err.println("500 ERROR BODY: " + response.getBody());
        }

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void unknownGatewayIs404() {
        ResponseEntity<String> response = http.exchange(
                "/api/v1/edge/sync",
                HttpMethod.GET,
                new HttpEntity<>(edgeHeaders("unknown-gw-999", "random-key")),
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void syncMarksGatewayOnline() {
        assertEquals(GatewayStatus.PROVISIONED, sampleGateway.getStatus());
        assertNull(sampleGateway.getLastSyncAt());

        ResponseEntity<String> response = http.exchange(
                "/api/v1/edge/sync",
                HttpMethod.GET,
                new HttpEntity<>(edgeHeaders(sampleGateway.getGatewayCode(), plainApiKey)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        EdgeGateway updated = gatewayRepository.findById(sampleGateway.getId()).orElseThrow();
        assertEquals(GatewayStatus.ONLINE, updated.getStatus());
        assertNotNull(updated.getLastSyncAt());
    }

    @Test
    void deviceChangePublishesSyncRequestedAfterCommit() throws Exception {
        String testQueue = "test.spy.edge-sync-requested";
        amqpAdmin.declareQueue(new Queue(testQueue, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue, true, false, false))
                .to(new TopicExchange("edifika.edge.commands"))
                .with(sampleGateway.getGatewayCode() + ".#"));

        Map<String, Object> req = Map.of(
                "deviceCode", "esp32-sync-test",
                "gatewayId", sampleGateway.getId(),
                "name", "Puerta Sync Test",
                "location", "Piso 1",
                "deviceType", "DOOR",
                "capabilities", List.of("RFID", "LOCK"),
                "unlockSeconds", 5
        );

        ResponseEntity<String> createRes = http.postForEntity("/api/v1/iot/devices", new HttpEntity<>(req, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());

        Message received = rabbitTemplate.receive(testQueue, 5000);
        assertNotNull(received, "Should receive SyncRequested on spy queue");

        String body = new String(received.getBody(), StandardCharsets.UTF_8);
        var envelope = objectMapper.readTree(body);
        assertEquals("SyncRequested", envelope.get("eventType").asText());
        assertEquals(sampleGateway.getGatewayCode() + ".sync-requested", received.getMessageProperties().getReceivedRoutingKey());

        long payloadVersion = envelope.get("payload").get("syncVersion").asLong();
        assertTrue(payloadVersion >= 2, "Sync version should be bumped");

        amqpAdmin.deleteQueue(testQueue);
    }
}
