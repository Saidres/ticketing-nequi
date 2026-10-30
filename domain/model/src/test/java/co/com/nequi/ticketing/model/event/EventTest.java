package co.com.nequi.ticketing.model.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private static final Instant DATE = Instant.parse("2026-12-01T20:00:00Z");

    @Test
    void shouldCreateValidEvent() {
        Event event = new Event("E1", "Rock Fest", DATE, "Movistar Arena", 100);

        assertEquals("Rock Fest", event.name());
        assertEquals(100, event.totalCapacity());
    }

    @Test
    void shouldRequireId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event(null, "Rock Fest", DATE, "Movistar Arena", 100));
    }

    @Test
    void shouldRequireDate() {
        assertThrows(NullPointerException.class,
                () -> new Event("E1", "Rock Fest", null, "Movistar Arena", 100));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankName(String name) {
        assertThrows(IllegalArgumentException.class,
                () -> new Event("E1", name, DATE, "Movistar Arena", 100));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankVenue(String venue) {
        assertThrows(IllegalArgumentException.class,
                () -> new Event("E1", "Rock Fest", DATE, venue, 100));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, Event.MAX_CAPACITY + 1})
    void shouldRejectCapacityOutOfRange(int capacity) {
        assertThrows(IllegalArgumentException.class,
                () -> new Event("E1", "Rock Fest", DATE, "Movistar Arena", capacity));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Event.MAX_CAPACITY})
    void shouldAcceptCapacityAtLimits(int capacity) {
        Event event = new Event("E1", "Rock Fest", DATE, "Movistar Arena", capacity);

        assertEquals(capacity, event.totalCapacity());
    }
}
