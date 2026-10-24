package com.edifika.access;

import com.edifika.access.access.application.internal.outboundservices.messaging.IntegrationEventPublisher;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import com.edifika.access.support.ContractAssertions;
import com.edifika.access.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RabbitMqConfigurationIT {

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private IntegrationEventPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void declaresQueueWithDeadLetter() {
        var queueProps = amqpAdmin.getQueueProperties("access.edge-events");
        assertNotNull(queueProps, "access.edge-events queue should exist");
        var dlqProps = amqpAdmin.getQueueProperties("access.edge-events.dlq");
        assertNotNull(dlqProps, "access.edge-events.dlq queue should exist");
    }

    @Test
    void publishedEnvelopeMatchesContract() {
        String testQueue = "test.spy.access-events";
        amqpAdmin.declareQueue(new Queue(testQueue, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue, true, false, false))
                .to(new TopicExchange("edifika.events"))
                .with("access.#"));

        Map<String, Object> payload = Map.of(
                "attemptId", 101,
                "deviceId", 3,
                "deviceCode", "esp32-acc-01",
                "userId", 15,
                "credentialId", 9,
                "accessMode", "RESERVED",
                "occurredAt", "2026-10-23T15:04:05Z"
        );

        IntegrationEnvelope envelope = IntegrationEnvelope.of(
                "PhysicalAccessGranted",
                12L,
                "edge-gateway-01",
                null,
                payload
        );

        publisher.publish("access.physical-access-granted", envelope);

        Message received = rabbitTemplate.receive(testQueue, 5000);
        assertNotNull(received, "Message should be received from spy queue");

        String body = new String(received.getBody(), StandardCharsets.UTF_8);
        ContractAssertions.assertMatches("contracts/envelope.schema.json", body);
        assertEquals("PhysicalAccessGranted", received.getMessageProperties().getType());
        assertEquals(MessageDeliveryMode.PERSISTENT, received.getMessageProperties().getReceivedDeliveryMode());
        assertEquals("application/json", received.getMessageProperties().getContentType());
        assertEquals(envelope.eventId(), received.getMessageProperties().getMessageId());
        assertEquals("iot-access-management", received.getMessageProperties().getHeader("x-producer"));

        amqpAdmin.deleteQueue(testQueue);
    }
}
