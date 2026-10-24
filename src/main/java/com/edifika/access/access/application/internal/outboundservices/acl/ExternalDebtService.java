package com.edifika.access.access.application.internal.outboundservices.acl;

import java.time.LocalDate;
import java.util.List;

public interface ExternalDebtService {

    record UnitDebt(Long id, LocalDate dueDate, String status) {
    }

    List<UnitDebt> debtsOfUnit(Long unitId);
}
