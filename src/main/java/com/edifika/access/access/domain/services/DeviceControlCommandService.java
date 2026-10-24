package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.commands.SetLedColorCommand;
import com.edifika.access.access.domain.model.entities.DeviceCommand;

import java.util.Optional;

public interface DeviceControlCommandService {

    DeviceCommand issueSetLedColor(SetLedColorCommand command);

    Optional<DeviceCommand> getCommand(String commandId);
}
