package co.com.nequi.ticketing.model.ticket;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TicketTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-11-01T10:10:00Z");
    private final Ticket available = Ticket.available("T1", "E1");

    @Test
    void shouldReserveAvailableTicket() {
        Ticket reserved = available.reserve("O1", EXPIRES_AT);

        assertEquals(TicketStatus.RESERVED, reserved.status());
        assertEquals("O1", reserved.orderId());
        assertEquals(EXPIRES_AT, reserved.reservedUntil());
        assertEquals(1, reserved.version());
    }

    @Test
    void shouldNotReserveAlreadyReservedTicket() {
        Ticket reserved = available.reserve("O1", EXPIRES_AT);

        assertThrows(InvalidTicketTransitionException.class, () -> reserved.reserve("O2", EXPIRES_AT));
    }

    @Test
    void shouldCompleteSaleFlow() {
        Ticket sold = available.reserve("O1", EXPIRES_AT).startConfirmation("O1").confirm("O1");

        assertEquals(TicketStatus.SOLD, sold.status());
        assertNull(sold.reservedUntil());
        assertEquals(3, sold.version());
    }

    @Test
    void shouldRejectConfirmationFromAnotherOrder() {
        Ticket reserved = available.reserve("O1", EXPIRES_AT);

        assertThrows(TicketNotOwnedException.class, () -> reserved.startConfirmation("O2"));
    }

    @Test
    void shouldRejectSaleFromAnotherOrder() {
        Ticket pending = available.reserve("O1", EXPIRES_AT).startConfirmation("O1");

        assertThrows(TicketNotOwnedException.class, () -> pending.confirm("O2"));
    }


    @Test
    void shouldReleaseReservedTicket() {
        Ticket released = available.reserve("O1", EXPIRES_AT).release();

        assertEquals(TicketStatus.AVAILABLE, released.status());
        assertNull(released.orderId());
        assertNull(released.reservedUntil());
    }

    @Test
    void shouldGrantComplimentaryTicket() {
        Ticket complimentary = available.grantComplimentary("O1");

        assertEquals(TicketStatus.COMPLIMENTARY, complimentary.status());
    }

    @Test
    void shouldDetectExpiredReservation() {
        Ticket reserved = available.reserve("O1", EXPIRES_AT);

        assertTrue(reserved.isReservationExpired(EXPIRES_AT.plusSeconds(1)));
        assertFalse(reserved.isReservationExpired(EXPIRES_AT.minusSeconds(1)));
    }

    @Test
    void shouldNotExpireTicketThatIsNotReserved() {
        assertFalse(available.isReservationExpired(EXPIRES_AT));
    }

    @Test
    void shouldRequireMandatoryFields() {
        assertThrows(NullPointerException.class,
                () -> new Ticket(null, "E1", TicketStatus.AVAILABLE, null, null, 0));
    }
}
