package com.edifika.access.access.infrastructure.external.residential;

import com.edifika.access.access.application.internal.outboundservices.acl.ExternalResidentDirectory;
import com.edifika.access.access.domain.exceptions.ExternalServiceUnavailableException;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.external.ServiceTokenProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class RestResidentialDirectory implements ExternalResidentDirectory {

    private final RestClient restClient;
    private final IntegrationProperties properties;
    private final ServiceTokenProvider tokenProvider;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResidentialUserUnitDto(
            Long idUserUnit,
            Long idUnit,
            Long idUser,
            String startDate,
            String endDate,
            String status
    ) {
    }

    public RestResidentialDirectory(
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
    public List<ResidentUnit> residentsOfBuilding(Long buildingId) {
        String url = properties.getResidentialUrl() + "/api/v1/residential/buildings/" + buildingId + "/residents";
        try {
            List<ResidentialUserUnitDto> dtos = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + tokenProvider.token())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new ExternalServiceUnavailableException("Servicio residencial devolvió error 5xx: " + resp.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<List<ResidentialUserUnitDto>>() {});

            if (dtos == null) {
                return List.of();
            }

            return dtos.stream()
                    .filter(dto -> dto.idUser() != null && dto.idUnit() != null)
                    .map(dto -> new ResidentUnit(
                            dto.idUser(),
                            dto.idUnit(),
                            "ACTIVE".equalsIgnoreCase(dto.status())
                    ))
                    .toList();
        } catch (ExternalServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Error comunicándose con el servicio residencial en {}: {}", url, ex.getMessage());
            throw new ExternalServiceUnavailableException("Servicio residencial no disponible: " + ex.getMessage(), ex);
        }
    }
}
