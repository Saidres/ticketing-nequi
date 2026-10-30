package co.com.nequi.ticketing.model.ticket;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketStatusTest {

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, AVAILABLE, false",
            "AVAILABLE, RESERVED, true",
            "AVAILABLE, PENDING_CONFIRMATION, false",
            "AVAILABLE, SOLD, false",
            "AVAILABLE, COMPLIMENTARY, true",

            "RESERVED, AVAILABLE, true",
            "RESERVED, RESERVED, false",
            "RESERVED, PENDING_CONFIRMATION, true",
            "RESERVED, SOLD, false",
            "RESERVED, COMPLIMENTARY, false",

            "PENDING_CONFIRMATION, AVAILABLE, false",
            "PENDING_CONFIRMATION, RESERVED, false",
            "PENDING_CONFIRMATION, PENDING_CONFIRMATION, false",
            "PENDING_CONFIRMATION, SOLD, true",
            "PENDING_CONFIRMATION, COMPLIMENTARY, false",

            "SOLD, AVAILABLE, false",
            "SOLD, RESERVED, false",
            "SOLD, PENDING_CONFIRMATION, false",
            "SOLD, SOLD, false",
            "SOLD, COMPLIMENTARY, false",

            "COMPLIMENTARY, AVAILABLE, false",
            "COMPLIMENTARY, RESERVED, false",
            "COMPLIMENTARY, PENDING_CONFIRMATION, false",
            "COMPLIMENTARY, SOLD, false",
            "COMPLIMENTARY, COMPLIMENTARY, false",
    })
    void shouldValidateTransitions(TicketStatus from, TicketStatus to, boolean expected) {
        assertEquals(expected, from.canTransitionTo(to));
    }

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, false",
            "RESERVED, true",
            "PENDING_CONFIRMATION, true",
            "SOLD, true",
            "COMPLIMENTARY, false",
    })
    void shouldKnowWhichStatesAreHeldByAnOrder(TicketStatus status, boolean expected) {
        assertEquals(expected, status.isHeldByOrder());
    }
}
