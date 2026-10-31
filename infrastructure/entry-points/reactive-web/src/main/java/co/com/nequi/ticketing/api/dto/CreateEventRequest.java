package co.com.nequi.ticketing.api.dto;

import co.com.nequi.ticketing.usecase.createevent.CreateEventCommand;

import java.time.Instant;

public record CreateEventRequest(String name, Instant date, String venue, int totalCapacity) {

    public CreateEventCommand toCommand() {
        return new CreateEventCommand(name, date, venue, totalCapacity);
    }
}
