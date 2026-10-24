package com.edifika.access;

import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.support.ContractAssertions;
import com.edifika.access.support.JwtTestTokens;
import com.edifika.access.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IotDeviceControllerIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private EdgeGatewayRepository gatewayRepository;

    @Autowired
    private IotDeviceRepository deviceRepository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Long sampleGatewayId;

    @BeforeEach
    void setup() {
        deviceRepository.deleteAll();
        gatewayRepository.deleteAll();

        var issue = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A"));
        EdgeGateway saved = gatewayRepository.save(issue.gateway());
        sampleGatewayId = saved.getId();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTestTokens.admin());
        return headers;
    }

    private HttpHeaders residentHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTestTokens.resident(15L));
        return headers;
    }

    @Test
    void adminCreatesDeviceAndResponseMatchesContract() throws Exception {
        Map<String, Object> req = Map.of(
                "deviceCode", "esp32-acc-01",
                "gatewayId", sampleGatewayId,
                "name", "Puerta piscina",
                "location", "Piso 1",
                "deviceType", "DOOR",
                "capabilities", List.of("RFID", "LOCK", "BUZZER", "OLED", "RGB_LED", "TEMPERATURE", "HUMIDITY"),
                "unlockSeconds", 5
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, adminHeaders());
        ResponseEntity<String> response = http.postForEntity("/api/v1/iot/devices", entity, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        ContractAssertions.assertMatches("contracts/rest/access/DeviceResource.schema.json", response.getBody());
    }

    @Test
    void duplicateDeviceCodeIs409() {
        Map<String, Object> req = Map.of(
                "deviceCode", "esp32-acc-dup",
                "gatewayId", sampleGatewayId,
                "name", "Puerta 1",
                "location", "Piso 1",
                "deviceType", "DOOR",
                "capabilities", List.of("RFID", "LOCK"),
                "unlockSeconds", 5
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(req, adminHeaders());
        ResponseEntity<String> r1 = http.postForEntity("/api/v1/iot/devices", entity, String.class);
        assertEquals(HttpStatus.CREATED, r1.getStatusCode());

        ResponseEntity<String> r2 = http.postForEntity("/api/v1/iot/devices", entity, String.class);
        assertEquals(HttpStatus.CONFLICT, r2.getStatusCode());
    }

    @Test
    void residentCannotListDevices() {
        HttpEntity<Void> entity = new HttpEntity<>(residentHeaders());
        ResponseEntity<String> response = http.exchange("/api/v1/iot/devices", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createDevicePublishesDeviceRegistered() throws Exception {
        String testQueue = "test.spy.device-registry";
        amqpAdmin.declareQueue(new Queue(testQueue, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue, true, false, false))
                .to(new TopicExchange("edifika.events"))
                .with("iot.#"));

        Map<String, Object> req = Map.of(
                "deviceCode", "esp32-acc-pub",
                "gatewayId", sampleGatewayId,
                "name", "Puerta principal",
                "location", "Entrada",
                "deviceType", "DOOR",
                "capabilities", List.of("RFID", "LOCK", "RGB_LED", "TEMPERATURE", "HUMIDITY"),
                "unlockSeconds", 5
        );

        ResponseEntity<String> response = http.postForEntity("/api/v1/iot/devices", new HttpEntity<>(req, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        Message received = rabbitTemplate.receive(testQueue, 5000);
        assertNotNull(received, "Should receive message on spy queue");

        String body = new String(received.getBody(), StandardCharsets.UTF_8);
        var envelope = objectMapper.readTree(body);
        assertEquals("DeviceRegistered", envelope.get("eventType").asText());
        assertEquals("iot.device-registered", received.getMessageProperties().getReceivedRoutingKey());

        ContractAssertions.assertMatches("contracts/payloads/DeviceRegistered.schema.json", envelope.get("payload").toString());

        amqpAdmin.deleteQueue(testQueue);
    }

    @Test
    void decommissionReturns204AndPublishesEvent() throws Exception {
        String testQueue = "test.spy.decommission";
        amqpAdmin.declareQueue(new Queue(testQueue, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue, true, false, false))
                .to(new TopicExchange("edifika.events"))
                .with("iot.#"));

        Map<String, Object> req = Map.of(
                "deviceCode", "esp32-acc-dec",
                "gatewayId", sampleGatewayId,
                "name", "Puerta auxiliar",
                "location", "Piso 2",
                "deviceType", "DOOR",
                "capabilities", List.of("RFID", "LOCK"),
                "unlockSeconds", 5
        );

        ResponseEntity<String> createRes = http.postForEntity("/api/v1/iot/devices", new HttpEntity<>(req, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        var created = objectMapper.readTree(createRes.getBody());
        long deviceId = created.get("id").asLong();

        // Drain the registration event
        rabbitTemplate.receive(testQueue, 3000);

        ResponseEntity<Void> deleteRes = http.exchange(
                "/api/v1/iot/devices/" + deviceId,
                HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, deleteRes.getStatusCode());

        Message received = rabbitTemplate.receive(testQueue, 5000);
        assertNotNull(received, "Should receive decommission event");

        String body = new String(received.getBody(), StandardCharsets.UTF_8);
        var envelope = objectMapper.readTree(body);
        assertEquals("DeviceDecommissioned", envelope.get("eventType").asText());
        ContractAssertions.assertMatches("contracts/payloads/DeviceDecommissioned.schema.json", envelope.get("payload").toString());

        amqpAdmin.deleteQueue(testQueue);
    }
}
