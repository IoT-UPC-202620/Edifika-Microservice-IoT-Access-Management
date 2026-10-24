package com.edifika.access.access.application.internal.outboundservices.acl;

import java.util.List;

public interface ExternalResidentDirectory {

    record ResidentUnit(Long userId, Long unitId, boolean active) {
    }

    List<ResidentUnit> residentsOfBuilding(Long buildingId);
}
