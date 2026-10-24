package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.queries.GetCredentialByIdQuery;
import com.edifika.access.access.domain.model.queries.GetCredentialsByUserQuery;
import com.edifika.access.access.domain.model.queries.GetCredentialsQuery;

import java.util.List;
import java.util.Optional;

public interface CredentialQueryService {

    List<AccessCredential> getCredentials(GetCredentialsQuery query);

    List<AccessCredential> getCredentialsByUser(GetCredentialsByUserQuery query);

    Optional<AccessCredential> getCredentialById(GetCredentialByIdQuery query);

    Optional<AccessCredential> getCredentialByUid(String rfidUid);
}
