package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.commands.ChangeCredentialStatusCommand;
import com.edifika.access.access.domain.model.commands.CreateCredentialCommand;
import com.edifika.access.access.domain.model.commands.UpsertEdgeCredentialCommand;

import java.util.Optional;

public interface CredentialCommandService {

    Optional<AccessCredential> createCredential(CreateCredentialCommand command);

    Optional<AccessCredential> changeStatus(ChangeCredentialStatusCommand command);

    void upsertFromEdge(UpsertEdgeCredentialCommand command);
}
