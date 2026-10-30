package co.com.nequi.ticketing.model.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private final Order pending = Order.pending("O1", "E1", "C1", 2, NOW);

    @Test
    void shouldStartAsPending() {
        assertEquals(OrderStatus.PENDING, pending.status());
    }

    @Test
    void shouldReservePendingOrder() {
        assertEquals(OrderStatus.RESERVED, pending.reserve().status());
    }

    @Test
    void shouldRejectPendingOrder() {
        assertEquals(OrderStatus.REJECTED, pending.reject().status());
    }

    @Test
    void shouldConfirmReservedOrder() {
        assertEquals(OrderStatus.CONFIRMED, pending.reserve().confirm().status());
    }

    @Test
    void shouldExpireReservedOrder() {
        assertEquals(OrderStatus.EXPIRED, pending.reserve().expire().status());
    }

    @Test
    void shouldNotConfirmPendingOrder() {
        assertThrows(InvalidOrderTransitionException.class, pending::confirm);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void shouldRejectQuantityOutOfRange(int quantity) {
        assertThrows(IllegalArgumentException.class,
                () -> Order.pending("O1", "E1", "C1", quantity, NOW));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6})
    void shouldAcceptQuantityAtLimits(int quantity) {
        assertEquals(quantity, Order.pending("O1", "E1", "C1", quantity, NOW).quantity());
    }

    @Test
    void shouldRequireCustomer() {
        assertThrows(NullPointerException.class,
                () -> Order.pending("O1", "E1", null, 2, NOW));
    }
}
