package co.com.nequi.ticketing.model.order;

import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.ReservationExpiredException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static co.com.nequi.ticketing.model.shared.Preconditions.requireNonBlank;

public record Order(String id, String eventId, String customerId, int quantity, OrderStatus status,
                    Instant createdAt, Instant reservedUntil, long version) {

    public static final int MAX_TICKETS_PER_ORDER = 6;

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

    public static Order pending(String id, String eventId, String customerId, int quantity, Instant createdAt) {
        return new Order(id, eventId, customerId, quantity, OrderStatus.PENDING, createdAt, null, 0L);
    }

    public static Duration requireValidReservationTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_RESERVATION_TTL) > 0) {
            throw new IllegalArgumentException("ttl must be between 1 second and " + MAX_RESERVATION_TTL);
        }
        return ttl;
    }

    public Order reserve(Instant now, Duration ttl) {
        return transitionTo(OrderStatus.RESERVED, now.plus(requireValidReservationTtl(ttl)));
    }

    public Order reject() {
        return transitionTo(OrderStatus.REJECTED, null);
    }

    // Persist before moving tickets: the version check decides the race against expiration
    public Order confirm(Instant now) {
        if (isReservationExpired(now)) {
            throw new ReservationExpiredException(id);
        }
        return transitionTo(OrderStatus.CONFIRMED, null);
    }

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
