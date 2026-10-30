package co.com.nequi.ticketing.model.order;

import java.time.Instant;
import java.util.Objects;

public record Order(String id, String eventId, String customerId, int quantity,
                    OrderStatus status, Instant createdAt) {

    // Security: caps tickets per order to prevent resource abuse
    public static final int MAX_TICKETS_PER_ORDER = 6;

    public Order {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (quantity < 1 || quantity > MAX_TICKETS_PER_ORDER) {
            throw new IllegalArgumentException(
                    "quantity must be between 1 and %d".formatted(MAX_TICKETS_PER_ORDER));
        }
    }

    // Req. 3: every purchase request starts queued as PENDING
    public static Order pending(String id, String eventId, String customerId, int quantity, Instant createdAt) {
        return new Order(id, eventId, customerId, quantity, OrderStatus.PENDING, createdAt);
    }

    // Req. 3: the consumer secured the tickets
    public Order reserve() {
        return transitionTo(OrderStatus.RESERVED);
    }

    // Req. 3 & 5: not enough tickets available
    public Order reject() {
        return transitionTo(OrderStatus.REJECTED);
    }

    public Order confirm() {
        return transitionTo(OrderStatus.CONFIRMED);
    }

    // Req. 6: reservation time limit exceeded
    public Order expire() {
        return transitionTo(OrderStatus.EXPIRED);
    }

    private Order transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderTransitionException(id, status, target);
        }
        return new Order(id, eventId, customerId, quantity, target, createdAt);
    }
}
