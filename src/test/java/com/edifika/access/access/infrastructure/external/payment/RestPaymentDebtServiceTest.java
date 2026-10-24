package com.edifika.access.access.infrastructure.external.payment;

import com.edifika.access.access.application.internal.outboundservices.acl.ExternalDebtService;
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

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class RestPaymentDebtServiceTest {

    private WireMockServer wireMock;
    private RestPaymentDebtService debtService;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        IntegrationProperties props = new IntegrationProperties();
        props.setPaymentUrl("http://localhost:" + wireMock.port());
        props.setTimeoutSeconds(2);

        ServiceTokenProvider tokenProvider = new ServiceTokenProvider("edifika-secret-key-must-be-at-least-32-characters-long");

        debtService = new RestPaymentDebtService(
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
    void paymentClientSendsServiceTokenAndParsesRows() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/payments/debts/unit/101"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id": 1, "unitId": 101, "description": "Mantenimiento Octubre", "amount": 150.0, "currency": "PEN", "dueDate": "2026-10-15", "status": "OVERDUE"},
                                  {"id": 2, "unitId": 101, "description": "Agua Octubre", "amount": 40.0, "currency": "PEN", "dueDate": "2026-10-28", "status": "PENDING"}
                                ]
                                """)
                ));

        List<ExternalDebtService.UnitDebt> result = debtService.debtsOfUnit(101L);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("OVERDUE", result.get(0).status());
        assertEquals(LocalDate.of(2026, 10, 15), result.get(0).dueDate());

        assertEquals(2L, result.get(1).id());
        assertEquals("PENDING", result.get(1).status());
        assertEquals(LocalDate.of(2026, 10, 28), result.get(1).dueDate());

        wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/payments/debts/unit/101"))
                .withHeader("Authorization", matching("Bearer .+")));
    }

    @Test
    void paymentClientMaps5xxToExternalServiceUnavailable() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/payments/debts/unit/101"))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class, () ->
                debtService.debtsOfUnit(101L)
        );
    }

    @Test
    void paymentClient404MeansNoDebts() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/payments/debts/unit/101"))
                .willReturn(aResponse().withStatus(404)));

        List<ExternalDebtService.UnitDebt> result = debtService.debtsOfUnit(101L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
