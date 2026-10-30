package co.com.nequi.ticketing.model.event;

import java.time.Instant;
import java.util.Objects;

import static co.com.nequi.ticketing.model.shared.Preconditions.requireNonBlank;

public record Event(String id, String name, Instant date, String venue, int totalCapacity) {

    public static final int MAX_CAPACITY = 100_000;

    public Event {
        requireNonBlank(id, "id");
        requireNonBlank(name, "name");
        requireNonBlank(venue, "venue");
        Objects.requireNonNull(date, "date is required");
        if (totalCapacity < 1 || totalCapacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("totalCapacity must be between 1 and %d".formatted(MAX_CAPACITY));
        }
    }
}
