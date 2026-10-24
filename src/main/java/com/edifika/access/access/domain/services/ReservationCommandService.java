package com.edifika.access.access.domain.services;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.commands.AddAuthorizationCommand;
import com.edifika.access.access.domain.model.commands.CancelReservationCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.commands.RemoveAuthorizationCommand;
import com.edifika.access.access.domain.model.entities.DoorAuthorization;

public interface ReservationCommandService {

    DoorReservation createReservation(CreateReservationCommand command);

    void cancelReservation(CancelReservationCommand command);

    DoorAuthorization addAuthorization(AddAuthorizationCommand command);

    void removeAuthorization(RemoveAuthorizationCommand command);
}
