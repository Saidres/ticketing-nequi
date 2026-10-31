package co.com.nequi.ticketing.api.dto;

import co.com.nequi.ticketing.model.event.Event;

import java.time.Instant;

public record EventResponse(String eventId, String name, Instant date, String venue, int totalCapacity) {

    public static EventResponse of(Event event) {
        return new EventResponse(event.id(), event.name(), event.date(), event.venue(), event.totalCapacity());
    }
}
