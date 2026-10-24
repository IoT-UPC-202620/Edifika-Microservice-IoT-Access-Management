package com.edifika.access.access.application.internal.queryservices;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.queries.GetCredentialByIdQuery;
import com.edifika.access.access.domain.model.queries.GetCredentialsByUserQuery;
import com.edifika.access.access.domain.model.queries.GetCredentialsQuery;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.access.domain.services.CredentialQueryService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CredentialQueryServiceImpl implements CredentialQueryService {

    private final AccessCredentialRepository credentialRepository;

    public CredentialQueryServiceImpl(AccessCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    @Override
    public List<AccessCredential> getCredentials(GetCredentialsQuery query) {
        return credentialRepository.search(query.buildingId(), query.userId(), query.status());
    }

    @Override
    public List<AccessCredential> getCredentialsByUser(GetCredentialsByUserQuery query) {
        return credentialRepository.findAllByUserId(query.userId());
    }

    @Override
    public Optional<AccessCredential> getCredentialById(GetCredentialByIdQuery query) {
        return credentialRepository.findById(query.credentialId());
    }

    @Override
    public Optional<AccessCredential> getCredentialByUid(String rfidUid) {
        String normalized = new RfidUid(rfidUid).value();
        return credentialRepository.findByRfidUid(normalized);
    }
}
