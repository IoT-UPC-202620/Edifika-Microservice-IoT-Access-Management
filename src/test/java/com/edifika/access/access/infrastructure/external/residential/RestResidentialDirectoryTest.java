package com.edifika.access.access.infrastructure.external.residential;

import com.edifika.access.access.application.internal.outboundservices.acl.ExternalResidentDirectory;
import com.edifika.access.access.domain.exceptions.ExternalServiceUnavailableException;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.external.ServiceTokenProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class RestResidentialDirectoryTest {

    private WireMockServer wireMock;
    private RestResidentialDirectory directory;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        IntegrationProperties props = new IntegrationProperties();
        props.setResidentialUrl("http://localhost:" + wireMock.port());
        props.setTimeoutSeconds(2);

        ServiceTokenProvider tokenProvider = new ServiceTokenProvider("edifika-secret-key-must-be-at-least-32-characters-long");

        directory = new RestResidentialDirectory(
                RestClient.builder(),
                props,
                tokenProvider
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void residentialClientSendsServiceTokenAndParsesRows() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/residential/buildings/12/residents"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"idUserUnit": 1, "idUnit": 101, "idUser": 15, "startDate": "2026-01-01", "endDate": null, "status": "ACTIVE"},
                                  {"idUserUnit": 2, "idUnit": 102, "idUser": 16, "startDate": "2026-01-01", "endDate": "2026-06-01", "status": "INACTIVE"}
                                ]
                                """)
                ));

        List<ExternalResidentDirectory.ResidentUnit> result = directory.residentsOfBuilding(12L);

        assertEquals(2, result.size());
        assertEquals(15L, result.get(0).userId());
        assertEquals(101L, result.get(0).unitId());
        assertTrue(result.get(0).active());

        assertEquals(16L, result.get(1).userId());
        assertEquals(102L, result.get(1).unitId());
        assertFalse(result.get(1).active());

        wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/residential/buildings/12/residents"))
                .withHeader("Authorization", matching("Bearer .+")));
    }

    @Test
    void residentialClient5xxThrowsExternalServiceUnavailable() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/residential/buildings/12/residents"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () ->
                directory.residentsOfBuilding(12L)
        );
    }
}
