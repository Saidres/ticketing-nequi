package co.com.nequi.ticketing.usecase.createevent;

import java.time.Instant;

public record CreateEventCommand(String name, Instant date, String venue, int totalCapacity) {
}
