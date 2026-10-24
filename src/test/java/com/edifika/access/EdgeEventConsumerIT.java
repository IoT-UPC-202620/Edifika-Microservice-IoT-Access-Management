package com.edifika.access;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessAttemptRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ProcessedEventRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import com.edifika.access.support.ContractAssertions;
import com.edifika.access.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EdgeEventConsumerIT {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private EdgeGatewayRepository gatewayRepository;

    @Autowired
    private IotDeviceRepository deviceRepository;

    @Autowired
    private AccessCredentialRepository credentialRepository;

    @Autowired
    private AccessAttemptRepository attemptRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private EdgeGateway sampleGateway;
    private IotDevice sampleDevice;
    private AccessCredential sampleCredential;

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAll();
        processedEventRepository.deleteAll();
        credentialRepository.deleteAll();
        deviceRepository.deleteAll();
        gatewayRepository.deleteAll();

        var issue = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A"));
        sampleGateway = gatewayRepository.save(issue.gateway());

        IotDevice device = new IotDevice(
                new CreateDeviceCommand("esp32-door-it", sampleGateway.getId(), "Puerta Entrada", "Lobby", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                sampleGateway
        );
        sampleDevice = deviceRepository.save(device);

        AccessCredential cred = AccessCredential.createCloud(
                "04A1B2C3", 12L, 15L, "Juan", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(86400)
        );
        sampleCredential = credentialRepository.save(cred);
    }

    @Test
    void sameAccessAttemptThreeTimesIsStoredOnceAndPublishedOnce() throws Exception {
        String testQueue = "test.spy.physical-access";
        amqpAdmin.declareQueue(new Queue(testQueue, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue, true, false, false))
                .to(new TopicExchange("edifika.events"))
                .with("access.#"));

        String eventId = UUID.randomUUID().toString();
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                eventId,
                "PhysicalAccessAttempt",
                1,
                Instant.now(),
                "edge-gateway-01",
                12L,
                "edge-gateway-01",
                null,
                Map.of(
                        "attemptId", "att-" + eventId,
                        "deviceCode", sampleDevice.getDeviceCode(),
                        "rfidUid", "04A1B2C3",
                        "residentId", "15",
                        "result", "GRANTED",
                        "reason", "VALID_CREDENTIAL"
                )
        );

        // Send same event 3 times
        for (int i = 0; i < 3; i++) {
            rabbitTemplate.convertAndSend("edifika.events", "edge.access-attempt", envelope);
        }

        // Wait up to 5 seconds
        Thread.sleep(3000);

        // Assert only 1 persisted attempt in DB
        assertEquals(1, attemptRepository.count(), "Idempotency failed: should persist exactly 1 attempt");

        // Assert spy queue receives exactly 1 PhysicalAccessGranted message
        Message first = rabbitTemplate.receive(testQueue, 5000);
        assertNotNull(first, "Should receive 1 PhysicalAccessGranted message");

        String body = new String(first.getBody(), StandardCharsets.UTF_8);
        var root = objectMapper.readTree(body);
        assertEquals("PhysicalAccessGranted", root.get("eventType").asText());
        ContractAssertions.assertMatches("contracts/payloads/PhysicalAccessGranted.schema.json", root.get("payload").toString());

        // Subsequent message should not exist
        Message second = rabbitTemplate.receive(testQueue, 1500);
        assertNull(second, "Should not receive duplicate integration event");

        amqpAdmin.deleteQueue(testQueue);
    }

    @Test
    void malformedMessageGoesToDlq() throws Exception {
        // Send malformed non-JSON bytes directly to access.edge-events
        Message malformed = MessageBuilder.withBody("NOT_A_VALID_JSON".getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build();

        rabbitTemplate.send("", "access.edge-events", malformed);

        // Wait for retry exhaustion (3 attempts) -> DLQ
        Message dlqMsg = null;
        for (int i = 0; i < 15; i++) {
            dlqMsg = rabbitTemplate.receive("access.edge-events.dlq", 2000);
            if (dlqMsg != null) break;
        }

        assertNotNull(dlqMsg, "Malformed message should be routed to access.edge-events.dlq");
    }
}
