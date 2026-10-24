package com.edifika.access;

import com.edifika.access.access.domain.model.entities.ResidentStatus;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ResidentStatusRepository;
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
class ResidentStatusControllerIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ResidentStatusRepository residentStatusRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        residentStatusRepository.deleteAll();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.admin());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders residentHeaders(long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.resident(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void adminCanGetResidentStatusAndMatchesContract() throws Exception {
        ResidentStatus status = new ResidentStatus(15L, 12L, Set.of(101L, 102L), false, 0, Instant.now());
        residentStatusRepository.save(status);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/access/residents/15/status",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ContractAssertions.assertMatches("contracts/rest/access/ResidentStatusResource.schema.json", response.getBody());
    }

    @Test
    void residentCanGetOwnStatus() {
        ResidentStatus status = new ResidentStatus(15L, 12L, Set.of(101L), true, 2, Instant.now());
        residentStatusRepository.save(status);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/access/residents/15/status",
                HttpMethod.GET,
                new HttpEntity<>(residentHeaders(15L)),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void residentCannotGetOtherResidentStatus() {
        ResidentStatus status = new ResidentStatus(16L, 12L, Set.of(102L), false, 0, Instant.now());
        residentStatusRepository.save(status);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/access/residents/16/status",
                HttpMethod.GET,
                new HttpEntity<>(residentHeaders(15L)),
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void unknownUserStatusIs404() {
        ResponseEntity<String> response = http.exchange(
                "/api/v1/access/residents/999/status",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void residentCannotTriggerDebtCheck() {
        ResponseEntity<String> response = http.exchange(
                "/api/v1/access/residents/15/debt-check",
                HttpMethod.POST,
                new HttpEntity<>(residentHeaders(15L)),
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
