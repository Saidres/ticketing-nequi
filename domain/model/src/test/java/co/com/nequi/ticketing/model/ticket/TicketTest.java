package co.com.nequi.ticketing.model.ticket;

import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.TicketNotOwnedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketTest {

    private final Ticket available = Ticket.available("T1", "E1");

    @Test
    void shouldReserveAvailableTicket() {
        Ticket reserved = available.reserve("O1");

        assertEquals(TicketStatus.RESERVED, reserved.status());
        assertEquals("O1", reserved.orderId());
        assertEquals(1, reserved.version());
    }

    @Test
    void shouldKeepIdentityAcrossTransitions() {
        Ticket reserved = available.reserve("O1");

        assertEquals("T1", reserved.id());
        assertEquals("E1", reserved.eventId());
    }

    @Test
    void shouldNotReserveAlreadyReservedTicket() {
        Ticket reserved = available.reserve("O1");

        assertThrows(InvalidStateTransitionException.class, () -> reserved.reserve("O2"));
    }

    @Test
    void shouldCompleteSaleFlow() {
        Ticket sold = available.reserve("O1").startConfirmation("O1").sell("O1");

        assertEquals(TicketStatus.SOLD, sold.status());
        assertEquals("O1", sold.orderId());
        assertEquals(3, sold.version());
    }

    @Test
    void shouldNotSellTicketThatIsOnlyReserved() {
        Ticket reserved = available.reserve("O1");

        assertThrows(InvalidStateTransitionException.class, () -> reserved.sell("O1"));
    }

    @Test
    void shouldRejectConfirmationFromAnotherOrder() {
        Ticket reserved = available.reserve("O1");

        assertThrows(TicketNotOwnedException.class, () -> reserved.startConfirmation("O2"));
    }

    @Test
    void shouldRejectSaleFromAnotherOrder() {
        Ticket pending = available.reserve("O1").startConfirmation("O1");

        assertThrows(TicketNotOwnedException.class, () -> pending.sell("O2"));
    }

    @Test
    void shouldReleaseReservedTicket() {
        Ticket released = available.reserve("O1").release("O1");

        assertEquals(TicketStatus.AVAILABLE, released.status());
        assertNull(released.orderId());
    }

    @Test
    void shouldNotReleaseTicketPendingConfirmation() {
        Ticket pending = available.reserve("O1").startConfirmation("O1");

        assertThrows(InvalidStateTransitionException.class, () -> pending.release("O1"));
    }

    @Test
    void shouldRequireOrderToReserve() {
        assertThrows(IllegalArgumentException.class, () -> available.reserve(null));
        assertThrows(IllegalArgumentException.class, () -> available.reserve(" "));
    }

    @Test
    void shouldNotReleaseTicketHeldByAnotherOrder() {
        Ticket reserved = available.reserve("O1");

        assertThrows(TicketNotOwnedException.class, () -> reserved.release("O2"));
    }

    @Test
    void shouldNotReleaseSoldTicket() {
        Ticket sold = available.reserve("O1").startConfirmation("O1").sell("O1");

        assertThrows(InvalidStateTransitionException.class, () -> sold.release("O1"));
    }

    @Test
    void shouldGrantComplimentaryTicket() {
        Ticket complimentary = available.grantComplimentary();

        assertEquals(TicketStatus.COMPLIMENTARY, complimentary.status());
        assertNull(complimentary.orderId());
    }

    @Test
    void shouldNotGrantReservedTicketAsComplimentary() {
        Ticket reserved = available.reserve("O1");

        assertThrows(InvalidStateTransitionException.class, reserved::grantComplimentary);
    }

    @Test
    void shouldRequireMandatoryFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket(null, "E1", TicketStatus.AVAILABLE, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket("T1", " ", TicketStatus.AVAILABLE, null, 0));
        assertThrows(NullPointerException.class,
                () -> new Ticket("T1", "E1", null, null, 0));
    }

    @Test
    void shouldRequireOrderWhenHeld() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket("T1", "E1", TicketStatus.RESERVED, null, 1));
    }

    @Test
    void shouldRejectOrderWhenAvailable() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ticket("T1", "E1", TicketStatus.AVAILABLE, "O1", 0));
    }
}
