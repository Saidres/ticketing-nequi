package co.com.nequi.ticketing.model.ticket;

import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.TicketNotOwnedException;

import java.util.Objects;

import static co.com.nequi.ticketing.model.shared.Preconditions.requireNonBlank;

public record Ticket(String id, String eventId, TicketStatus status, String orderId, long version) {

    public Ticket {
        requireNonBlank(id, "id");
        requireNonBlank(eventId, "eventId");
        Objects.requireNonNull(status, "status is required");
        boolean hasOrder = orderId != null;
        if (status.isHeldByOrder() != hasOrder) {
            throw new IllegalArgumentException("orderId is required only when status is " + status);
        }
    }

    public static Ticket available(String id, String eventId) {
        return new Ticket(id, eventId, TicketStatus.AVAILABLE, null, 0L);
    }

    public Ticket reserve(String orderId) {
        return transitionTo(TicketStatus.RESERVED, requireNonBlank(orderId, "orderId"));
    }

    public Ticket startConfirmation(String orderId) {
        requireHeldBy(orderId);
        return transitionTo(TicketStatus.PENDING_CONFIRMATION, orderId);
    }

    public Ticket sell(String orderId) {
        requireHeldBy(orderId);
        return transitionTo(TicketStatus.SOLD, orderId);
    }

    public Ticket release(String orderId) {
        requireHeldBy(orderId);
        return transitionTo(TicketStatus.AVAILABLE, null);
    }

    public Ticket grantComplimentary() {
        return transitionTo(TicketStatus.COMPLIMENTARY, null);
    }

    private Ticket transitionTo(TicketStatus target, String newOrderId) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Ticket", id, status, target);
        }
        return new Ticket(id, eventId, target, newOrderId, version + 1);
    }

    private void requireHeldBy(String orderId) {
        if (!Objects.equals(this.orderId, orderId)) {
            throw new TicketNotOwnedException(id, orderId);
        }
    }
}
