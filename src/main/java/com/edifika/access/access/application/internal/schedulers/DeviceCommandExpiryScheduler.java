package com.edifika.access.access.application.internal.schedulers;

import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCommandStatus;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class DeviceCommandExpiryScheduler {

    private final DeviceCommandRepository commandRepository;

    public DeviceCommandExpiryScheduler(DeviceCommandRepository commandRepository) {
        this.commandRepository = commandRepository;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void expirePendingCommands() {
        Instant cutoff = Instant.now().minusSeconds(5);
        List<DeviceCommand> expiredList = commandRepository.findAllByStatusAndExpiresAtBefore(
                DeviceCommandStatus.PENDING, cutoff
        );

        for (DeviceCommand cmd : expiredList) {
            cmd.markExpired();
            commandRepository.save(cmd);
            log.info("Comando de dispositivo {} marcado como EXPIRED", cmd.getCommandId());
        }
    }
}
