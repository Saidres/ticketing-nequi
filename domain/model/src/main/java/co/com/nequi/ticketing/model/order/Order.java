package co.com.nequi.ticketing.model.order;

import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.ReservationExpiredException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static co.com.nequi.ticketing.model.shared.Preconditions.requireNonBlank;

public record Order(String id, String eventId, String customerId, int quantity, OrderStatus status,
                    Instant createdAt, Instant reservedUntil, long version) {

    // Security: caps tickets per order to prevent resource abuse
    public static final int MAX_TICKETS_PER_ORDER = 6;

    // Req. 2: the configured reservation time can never exceed 10 minutes
    public static final Duration MAX_RESERVATION_TTL = Duration.ofMinutes(10);

    public Order {
        requireNonBlank(id, "id");
        requireNonBlank(eventId, "eventId");
        requireNonBlank(customerId, "customerId");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (quantity < 1 || quantity > MAX_TICKETS_PER_ORDER) {
            throw new IllegalArgumentException(
                    "quantity must be between 1 and %d".formatted(MAX_TICKETS_PER_ORDER));
        }
        boolean isReserved = status == OrderStatus.RESERVED;
        boolean hasDeadline = reservedUntil != null;
        if (isReserved != hasDeadline) {
            throw new IllegalArgumentException("reservedUntil is required only while RESERVED");
        }
    }

    // Req. 3: every purchase request starts queued as PENDING
    public static Order pending(String id, String eventId, String customerId, int quantity, Instant createdAt) {
        return new Order(id, eventId, customerId, quantity, OrderStatus.PENDING, createdAt, null, 0L);
    }

    // Req. 2 & 3: the consumer secured the tickets; the reservation clock starts now
    public Order reserve(Instant now, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_RESERVATION_TTL) > 0) {
            throw new IllegalArgumentException("ttl must be between 1 second and " + MAX_RESERVATION_TTL);
        }
        return transitionTo(OrderStatus.RESERVED, now.plus(ttl));
    }

    // Req. 3 & 5: not enough tickets available
    public Order reject() {
        return transitionTo(OrderStatus.REJECTED, null);
    }

    // Payment is out of scope, so a confirmed order always ends with SOLD tickets.
    // The order must be confirmed (conditional write) BEFORE its tickets move to PENDING_CONFIRMATION:
    // the version check then decides the race against the expiration sweeper, which only expires RESERVED orders
    public Order confirm(Instant now) {
        if (isReservationExpired(now)) {
            throw new ReservationExpiredException(id);
        }
        return transitionTo(OrderStatus.CONFIRMED, null);
    }

    // Req. 6: reservation time limit exceeded
    public Order expire() {
        return transitionTo(OrderStatus.EXPIRED, null);
    }

    public boolean isReservationExpired(Instant now) {
        return status == OrderStatus.RESERVED && now.isAfter(reservedUntil);
    }

    private Order transitionTo(OrderStatus target, Instant newReservedUntil) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Order", id, status, target);
        }
        return new Order(id, eventId, customerId, quantity, target, createdAt, newReservedUntil, version + 1);
    }
}
