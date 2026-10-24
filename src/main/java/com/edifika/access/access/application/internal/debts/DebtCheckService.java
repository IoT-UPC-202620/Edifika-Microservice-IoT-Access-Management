package com.edifika.access.access.application.internal.debts;

import com.edifika.access.access.application.internal.outboundservices.acl.ExternalDebtService;
import com.edifika.access.access.application.internal.outboundservices.acl.ExternalResidentDirectory;
import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.ExternalServiceUnavailableException;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.entities.ResidentStatus;
import com.edifika.access.access.domain.model.events.CredentialCreatedEvent;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ResidentStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class DebtCheckService {

    private final ExternalResidentDirectory residentDirectory;
    private final ExternalDebtService debtService;
    private final ResidentStatusRepository residentStatusRepository;
    private final AccessCredentialRepository credentialRepository;
    private final EdgeGatewayRepository gatewayRepository;
    private final EdgeSyncNotifier edgeSyncNotifier;
    private final IntegrationProperties properties;

    public DebtCheckService(
            ExternalResidentDirectory residentDirectory,
            ExternalDebtService debtService,
            ResidentStatusRepository residentStatusRepository,
            AccessCredentialRepository credentialRepository,
            EdgeGatewayRepository gatewayRepository,
            EdgeSyncNotifier edgeSyncNotifier,
            IntegrationProperties properties) {
        this.residentDirectory = residentDirectory;
        this.debtService = debtService;
        this.residentStatusRepository = residentStatusRepository;
        this.credentialRepository = credentialRepository;
        this.gatewayRepository = gatewayRepository;
        this.edgeSyncNotifier = edgeSyncNotifier;
        this.properties = properties;
    }

    public void refreshBuilding(Long buildingId) {
        if (!properties.isDebtCheckEnabled()) {
            log.debug("Verificación de deuda deshabilitada por configuración");
            return;
        }

        Instant now = Instant.now();
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(zone);

        List<ExternalResidentDirectory.ResidentUnit> residentUnits;
        try {
            residentUnits = residentDirectory.residentsOfBuilding(buildingId);
        } catch (Exception ex) {
            log.warn("Fallo al consultar residentes del edificio {}: {}", buildingId, ex.getMessage());
            markBuildingError(buildingId, ex.getMessage(), now);
            throw (ex instanceof ExternalServiceUnavailableException esue) ? esue :
                    new ExternalServiceUnavailableException("Servicio residencial no disponible: " + ex.getMessage(), ex);
        }

        Map<Long, Set<Long>> userToUnits = residentUnits.stream()
                .filter(ExternalResidentDirectory.ResidentUnit::active)
                .collect(Collectors.groupingBy(
                        ExternalResidentDirectory.ResidentUnit::userId,
                        Collectors.mapping(ExternalResidentDirectory.ResidentUnit::unitId, Collectors.toSet())
                ));

        boolean anyCredentialChanged = false;

        for (Map.Entry<Long, Set<Long>> entry : userToUnits.entrySet()) {
            Long userId = entry.getKey();
            Set<Long> units = entry.getValue();

            int overdueCount = 0;
            boolean errorOccurred = false;
            String errorMsg = null;

            for (Long unitId : units) {
                try {
                    List<ExternalDebtService.UnitDebt> debts = debtService.debtsOfUnit(unitId);
                    for (ExternalDebtService.UnitDebt debt : debts) {
                        if (isOverdue(debt, today)) {
                            overdueCount++;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Fallo al consultar deudas de unidad {}: {}", unitId, ex.getMessage());
                    errorOccurred = true;
                    errorMsg = ex.getMessage();
                    break;
                }
            }

            Optional<ResidentStatus> existingOpt = residentStatusRepository.findByUserId(userId);
            if (errorOccurred) {
                if (existingOpt.isPresent()) {
                    ResidentStatus status = existingOpt.get();
                    status.markError(errorMsg, now);
                    residentStatusRepository.save(status);
                } else {
                    ResidentStatus status = new ResidentStatus(userId, buildingId, units, false, 0, now);
                    status.markError(errorMsg, now);
                    residentStatusRepository.save(status);
                }
                // Do not alter credentials on network error
                continue;
            }

            boolean delinquent = overdueCount > 0;
            ResidentStatus status;
            if (existingOpt.isPresent()) {
                status = existingOpt.get();
                status.markChecked(delinquent, overdueCount, now, units, buildingId);
            } else {
                status = new ResidentStatus(userId, buildingId, units, delinquent, overdueCount, now);
            }
            residentStatusRepository.save(status);

            // Update user credentials
            List<AccessCredential> credentials = credentialRepository.findAllByUserId(userId);
            for (AccessCredential credential : credentials) {
                if (delinquent) {
                    if (credential.getStatus() == CredentialStatus.ACTIVE) {
                        credential.suspendForDebt();
                        credentialRepository.save(credential);
                        anyCredentialChanged = true;
                    }
                } else {
                    if (credential.isSuspendedForDebt()) {
                        credential.clearDebtSuspension();
                        credentialRepository.save(credential);
                        anyCredentialChanged = true;
                    }
                }
            }
        }

        if (anyCredentialChanged) {
            edgeSyncNotifier.changedForBuilding(buildingId);
        }
    }

    public ResidentStatus refreshUser(Long userId) {
        if (!properties.isDebtCheckEnabled()) {
            return residentStatusRepository.findByUserId(userId)
                    .orElseGet(() -> new ResidentStatus(userId, 0L, Set.of(), false, 0, Instant.now()));
        }

        Optional<ResidentStatus> existing = residentStatusRepository.findByUserId(userId);
        if (existing.isPresent()) {
            Long buildingId = existing.get().getBuildingId();
            refreshBuilding(buildingId);
            return residentStatusRepository.findByUserId(userId).orElse(existing.get());
        }

        // Try to infer buildingId from credentials
        List<AccessCredential> credentials = credentialRepository.findAllByUserId(userId);
        if (!credentials.isEmpty()) {
            Long buildingId = credentials.get(0).getBuildingId();
            refreshBuilding(buildingId);
            return residentStatusRepository.findByUserId(userId)
                    .orElseGet(() -> new ResidentStatus(userId, buildingId, Set.of(), false, 0, Instant.now()));
        }

        // Try from buildings with gateways
        List<EdgeGateway> gateways = gatewayRepository.findAll();
        for (EdgeGateway gateway : gateways) {
            try {
                refreshBuilding(gateway.getBuildingId());
                Optional<ResidentStatus> found = residentStatusRepository.findByUserId(userId);
                if (found.isPresent()) {
                    return found.get();
                }
            } catch (Exception ignored) {
            }
        }

        return residentStatusRepository.findByUserId(userId)
                .orElseThrow(() -> new ExternalServiceUnavailableException("No se encontró estado de residente para usuario " + userId));
    }

    public boolean isDelinquentForReservation(Long userId) {
        if (!properties.isDebtCheckEnabled()) {
            return false;
        }

        try {
            ResidentStatus status = refreshUser(userId);
            return status.isDelinquent();
        } catch (ExternalServiceUnavailableException ex) {
            Optional<ResidentStatus> cached = residentStatusRepository.findByUserId(userId);
            if (cached.isPresent()) {
                return cached.get().isDelinquent();
            }
            throw ex;
        }
    }

    @EventListener
    public void onCredentialCreated(CredentialCreatedEvent event) {
        if (event.userId() != null) {
            try {
                refreshUser(event.userId());
            } catch (Exception ex) {
                log.warn("Error en verificación de deuda asíncrona tras crear credencial para usuario {}: {}",
                        event.userId(), ex.getMessage());
            }
        }
    }

    private boolean isOverdue(ExternalDebtService.UnitDebt debt, LocalDate today) {
        String status = debt.status();
        if ("OVERDUE".equalsIgnoreCase(status)) {
            return true;
        }
        if ("PENDING".equalsIgnoreCase(status) && debt.dueDate() != null && debt.dueDate().isBefore(today)) {
            return true;
        }
        return false;
    }

    private void markBuildingError(Long buildingId, String error, Instant now) {
        List<ResidentStatus> list = residentStatusRepository.findAllByBuildingId(buildingId);
        for (ResidentStatus status : list) {
            status.markError(error, now);
            residentStatusRepository.save(status);
        }
    }
}
