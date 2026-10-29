package co.com.nequi.ticketing.model.event;

import java.time.Instant;
import java.util.Objects;

public record Event(String id, String name, Instant date, String venue, int totalCapacity) {

    public Event {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(date, "date is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException("venue is required");
        }
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("totalCapacity must be greater than zero");
        }
    }
}
