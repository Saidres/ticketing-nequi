package co.com.nequi.ticketing.model.order;

import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.ReservationExpiredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Instant EXPIRES_AT = NOW.plus(TTL);
    private final Order pending = Order.pending("O1", "E1", "C1", 2, NOW);

    @Test
    void shouldStartAsPendingWithoutReservation() {
        assertEquals(OrderStatus.PENDING, pending.status());
        assertNull(pending.reservedUntil());
        assertEquals(0, pending.version());
    }

    @Test
    void shouldReserveForTheGivenTtl() {
        Order reserved = pending.reserve(NOW, TTL);

        assertEquals(OrderStatus.RESERVED, reserved.status());
        assertEquals(EXPIRES_AT, reserved.reservedUntil());
        assertEquals(1, reserved.version());
    }

    @Test
    void shouldKeepDataAcrossTransitions() {
        Order reserved = pending.reserve(NOW, TTL);

        assertEquals("E1", reserved.eventId());
        assertEquals("C1", reserved.customerId());
        assertEquals(2, reserved.quantity());
        assertEquals(NOW, reserved.createdAt());
    }

    @Test
    void shouldRejectPendingOrder() {
        Order rejected = pending.reject();

        assertEquals(OrderStatus.REJECTED, rejected.status());
        assertNull(rejected.reservedUntil());
    }

    @Test
    void shouldConfirmReservationAtTheTimeLimit() {
        Order confirmed = pending.reserve(NOW, TTL).confirm(EXPIRES_AT);

        assertEquals(OrderStatus.CONFIRMED, confirmed.status());
        assertNull(confirmed.reservedUntil());
    }

    @Test
    void shouldNotConfirmExpiredReservation() {
        Order reserved = pending.reserve(NOW, TTL);

        assertThrows(ReservationExpiredException.class, () -> reserved.confirm(EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    void shouldNotConfirmPendingOrder() {
        assertThrows(InvalidStateTransitionException.class, () -> pending.confirm(NOW));
    }

    @Test
    void shouldExpireReservedOrder() {
        Order expired = pending.reserve(NOW, TTL).expire();

        assertEquals(OrderStatus.EXPIRED, expired.status());
        assertNull(expired.reservedUntil());
    }

    @Test
    void shouldNotExpirePendingOrder() {
        assertThrows(InvalidStateTransitionException.class, pending::expire);
    }

    @Test
    void shouldNotRejectReservedOrder() {
        Order reserved = pending.reserve(NOW, TTL);

        assertThrows(InvalidStateTransitionException.class, reserved::reject);
    }

    @Test
    void shouldDetectExpiredReservationOnlyAfterTheTimeLimit() {
        Order reserved = pending.reserve(NOW, TTL);

        assertFalse(reserved.isReservationExpired(EXPIRES_AT));
        assertTrue(reserved.isReservationExpired(EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    void shouldNotExpireOrderThatIsNotReserved() {
        assertFalse(pending.isReservationExpired(EXPIRES_AT.plusSeconds(1)));
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
    void shouldRequireMandatoryFields() {
        assertThrows(IllegalArgumentException.class, () -> Order.pending(null, "E1", "C1", 2, NOW));
        assertThrows(IllegalArgumentException.class, () -> Order.pending("O1", " ", "C1", 2, NOW));
        assertThrows(IllegalArgumentException.class, () -> Order.pending("O1", "E1", "", 2, NOW));
        assertThrows(NullPointerException.class, () -> Order.pending("O1", "E1", "C1", 2, null));
    }

    @Test
    void shouldRequireReservationTimeOnlyWhileReserved() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("O1", "E1", "C1", 2, OrderStatus.RESERVED, NOW, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Order("O1", "E1", "C1", 2, OrderStatus.PENDING, NOW, EXPIRES_AT, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S", "PT10M1S"})
    void shouldRejectTtlOutsideAllowedRange(String ttl) {
        assertThrows(IllegalArgumentException.class, () -> pending.reserve(NOW, Duration.parse(ttl)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT1S", "PT10M"})
    void shouldAcceptTtlAtLimits(String ttl) {
        Order reserved = pending.reserve(NOW, Duration.parse(ttl));

        assertEquals(NOW.plus(Duration.parse(ttl)), reserved.reservedUntil());
    }
}
