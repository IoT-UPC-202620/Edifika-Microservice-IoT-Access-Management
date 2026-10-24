package com.edifika.access;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AccessCredentialControllerIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private AccessCredentialRepository credentialRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        credentialRepository.deleteAll();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTestTokens.admin());
        return headers;
    }

    @Test
    void responseMatchesContract() throws Exception {
        Map<String, Object> req = Map.of(
                "rfidUid", "04A1B2C3",
                "buildingId", 12,
                "userId", 15,
                "holderName", "Ana Perez",
                "validFrom", "2026-10-23T15:04:05Z",
                "validUntil", "2027-10-23T15:04:05Z"
        );

        ResponseEntity<String> response = http.postForEntity(
                "/api/v1/access/credentials",
                new HttpEntity<>(req, adminHeaders()),
                String.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        ContractAssertions.assertMatches("contracts/rest/access/CredentialResource.schema.json", response.getBody());
    }

    @Test
    void duplicateUidInOtherFormatIs409() {
        Map<String, Object> req1 = Map.of(
                "rfidUid", "04A1B2C3",
                "buildingId", 12,
                "userId", 15,
                "holderName", "Ana Perez",
                "validFrom", "2026-10-23T15:04:05Z",
                "validUntil", "2027-10-23T15:04:05Z"
        );
        ResponseEntity<String> r1 = http.postForEntity("/api/v1/access/credentials", new HttpEntity<>(req1, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, r1.getStatusCode());

        Map<String, Object> req2 = Map.of(
                "rfidUid", "04 a1:b2-c3",
                "buildingId", 12,
                "userId", 16,
                "holderName", "Carlos Ruiz",
                "validFrom", "2026-10-23T15:04:05Z",
                "validUntil", "2027-10-23T15:04:05Z"
        );
        ResponseEntity<String> r2 = http.postForEntity("/api/v1/access/credentials", new HttpEntity<>(req2, adminHeaders()), String.class);
        assertEquals(HttpStatus.CONFLICT, r2.getStatusCode());
    }

    @Test
    void adminChangesStatus() throws Exception {
        Map<String, Object> req = Map.of(
                "rfidUid", "04B2C3D4",
                "buildingId", 12,
                "userId", 15,
                "holderName", "Ana Perez",
                "validFrom", "2026-10-23T15:04:05Z",
                "validUntil", "2027-10-23T15:04:05Z"
        );
        ResponseEntity<String> createRes = http.postForEntity("/api/v1/access/credentials", new HttpEntity<>(req, adminHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        var created = objectMapper.readTree(createRes.getBody());
        long id = created.get("id").asLong();

        Map<String, Object> patchReq = Map.of("status", "SUSPENDED");
        ResponseEntity<String> patchRes = http.exchange(
                "/api/v1/access/credentials/" + id + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(patchReq, adminHeaders()),
                String.class
        );
        assertEquals(HttpStatus.OK, patchRes.getStatusCode());
        var patched = objectMapper.readTree(patchRes.getBody());
        assertEquals("SUSPENDED", patched.get("status").asText());
        assertNull(patched.get("suspensionReason").textValue());
    }
}
