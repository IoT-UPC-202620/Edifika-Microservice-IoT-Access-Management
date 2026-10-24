package com.edifika.access.access.domain.services;

import com.edifika.access.access.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.access.access.interfaces.rest.resources.DoorSummaryResource;

import java.time.Instant;
import java.util.List;

public interface DoorSummaryQueryService {

    List<DoorSummaryResource> doorsFor(AuthenticatedUser user, Instant now);
}
