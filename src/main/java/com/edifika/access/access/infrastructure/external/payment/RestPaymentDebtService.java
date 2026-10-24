package com.edifika.access.access.infrastructure.external.payment;

import com.edifika.access.access.application.internal.outboundservices.acl.ExternalDebtService;
import com.edifika.access.access.domain.exceptions.ExternalServiceUnavailableException;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.external.ServiceTokenProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class RestPaymentDebtService implements ExternalDebtService {

    private final RestClient restClient;
    private final IntegrationProperties properties;
    private final ServiceTokenProvider tokenProvider;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentDebtDto(
            Long id,
            Long unitId,
            String description,
            Object amount,
            String currency,
            LocalDate dueDate,
            String status
    ) {
    }

    public RestPaymentDebtService(
            RestClient.Builder restClientBuilder,
            IntegrationProperties properties,
            ServiceTokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;

        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<UnitDebt> debtsOfUnit(Long unitId) {
        String url = properties.getPaymentUrl() + "/api/v1/payments/debts/unit/" + unitId;
        try {
            List<PaymentDebtDto> dtos = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + tokenProvider.token())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        // 404 means no debts for this unit
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new ExternalServiceUnavailableException("Servicio de pagos devolvió error 5xx: " + resp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<List<PaymentDebtDto>>() {});

            if (dtos == null) {
                return List.of();
            }

            return dtos.stream()
                    .filter(d -> d.status() != null)
                    .map(d -> new UnitDebt(d.id(), d.dueDate(), d.status()))
                    .toList();
        } catch (HttpClientErrorException.NotFound ex) {
            // 404 Not Found: unit has no registered debts
            return List.of();
        } catch (ExternalServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Error comunicándose con el servicio de pagos en {}: {}", url, ex.getMessage());
            throw new ExternalServiceUnavailableException("Servicio de pagos no disponible: " + ex.getMessage(), ex);
        }
    }
}
