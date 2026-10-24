package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.commands.ApplyCommandResultCommand;
import com.edifika.access.access.domain.model.commands.ApplyDeviceStatusCommand;
import com.edifika.access.access.domain.model.commands.RecordAccessAttemptCommand;

public interface EdgeEventCommandService {

    void recordAccessAttempt(RecordAccessAttemptCommand command);

    void applyDeviceStatus(ApplyDeviceStatusCommand command);

    void applyCommandResult(ApplyCommandResultCommand command);
}
