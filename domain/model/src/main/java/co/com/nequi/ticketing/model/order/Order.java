package co.com.nequi.ticketing.model.order;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Order(String id, String eventId, String customerId, int quantity,
                    OrderStatus status, List<String> ticketIds, Instant createdAt) {

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
        ticketIds = ticketIds == null ? List.of() : List.copyOf(ticketIds);
    }

    // Req. 3: every purchase request starts queued as PENDING
    public static Order pending(String id, String eventId, String customerId, int quantity, Instant createdAt) {
        return new Order(id, eventId, customerId, quantity, OrderStatus.PENDING, List.of(), createdAt);
    }

    // Req. 3: the consumer secured the tickets
    public Order reserve(List<String> reservedTicketIds) {
        if (reservedTicketIds.size() != quantity) {
            throw new IllegalArgumentException(
                    "expected %d tickets but got %d".formatted(quantity, reservedTicketIds.size()));
        }
        return transitionTo(OrderStatus.RESERVED, reservedTicketIds);
    }

    // Req. 3 & 5: not enough tickets available
    public Order reject() {
        return transitionTo(OrderStatus.REJECTED, List.of());
    }

    public Order confirm() {
        return transitionTo(OrderStatus.CONFIRMED, ticketIds);
    }

    // Req. 6: reservation time limit exceeded
    public Order expire() {
        return transitionTo(OrderStatus.EXPIRED, ticketIds);
    }

    private Order transitionTo(OrderStatus target, List<String> newTicketIds) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderTransitionException(id, status, target);
        }
        return new Order(id, eventId, customerId, quantity, target, newTicketIds, createdAt);
    }
}
