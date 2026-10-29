package co.com.nequi.ticketing.model.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private final Order pending = Order.pending("O1", "E1", "C1", 2, NOW);

    @Test
    void shouldStartAsPendingWithoutTickets() {
        assertEquals(OrderStatus.PENDING, pending.status());
        assertTrue(pending.ticketIds().isEmpty());
    }

    @Test
    void shouldReserveWithRequestedTickets() {
        Order reserved = pending.reserve(List.of("T1", "T2"));

        assertEquals(OrderStatus.RESERVED, reserved.status());
        assertEquals(List.of("T1", "T2"), reserved.ticketIds());
    }

    @Test
    void shouldRejectReservationWithWrongTicketCount() {
        assertThrows(IllegalArgumentException.class, () -> pending.reserve(List.of("T1")));
    }

    @Test
    void shouldRejectOrderWithoutTickets() {
        Order rejected = pending.reject();

        assertEquals(OrderStatus.REJECTED, rejected.status());
        assertTrue(rejected.ticketIds().isEmpty());
    }

    @Test
    void shouldConfirmReservedOrder() {
        Order confirmed = pending.reserve(List.of("T1", "T2")).confirm();

        assertEquals(OrderStatus.CONFIRMED, confirmed.status());
    }

    @Test
    void shouldExpireReservedOrder() {
        Order expired = pending.reserve(List.of("T1", "T2")).expire();

        assertEquals(OrderStatus.EXPIRED, expired.status());
    }

    @Test
    void shouldNotConfirmPendingOrder() {
        assertThrows(InvalidOrderTransitionException.class, pending::confirm);
    }

    @Test
    void shouldNotBeModifiableFromOutside() {
        List<String> mutable = new ArrayList<>(List.of("T1", "T2"));
        Order reserved = pending.reserve(mutable);

        mutable.add("T3");

        assertEquals(2, reserved.ticketIds().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void shouldRejectQuantityOutOfRange(int quantity) {
        assertThrows(IllegalArgumentException.class,
                () -> Order.pending("O1", "E1", "C1", quantity, NOW));
    }

    @Test
    void shouldRequireCustomer() {
        assertThrows(NullPointerException.class,
                () -> Order.pending("O1", "E1", null, 2, NOW));
    }
}
