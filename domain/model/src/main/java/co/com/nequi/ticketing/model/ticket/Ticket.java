package co.com.nequi.ticketing.model.ticket;

import java.time.Instant;
import java.util.Objects;

public record Ticket(String id, String eventId, TicketStatus status,
                     String orderId, Instant reservedUntil, long version) {

    // A ticket holds exactly one status at a time

    public Ticket {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(status, "status is required");
    }


    // Req. 1: every event has an inventory of available tickets
    public static Ticket available(String id, String eventId) {
        return new Ticket(id, eventId, TicketStatus.AVAILABLE, null, null, 0L);
    }

    // Req. 2: temporarily hold the requested tickets (max 10 minutes)
    public Ticket reserve(String orderId, Instant reservedUntil) {
        return transitionTo(TicketStatus.RESERVED, orderId, reservedUntil);
    }

    public Ticket startConfirmation(String orderId) {
        requireHeldBy(orderId);
        return transitionTo(TicketStatus.PENDING_CONFIRMATION, orderId, reservedUntil);
    }

    public Ticket confirm(String orderId) {
        requireHeldBy(orderId);
        return transitionTo(TicketStatus.SOLD, orderId, null);
    }
    // Req. 2 & 6: tickets return to the available inventory
    public Ticket release() {
        return transitionTo(TicketStatus.AVAILABLE, null, null);
    }

    public Ticket grantComplimentary(String orderId) {
        return transitionTo(TicketStatus.COMPLIMENTARY, orderId, null);
    }

    // Req. 6: detects reservations that exceeded the time limit
    public boolean isReservationExpired(Instant now) {
        return status == TicketStatus.RESERVED && now.isAfter(reservedUntil);
    }

    private Ticket transitionTo(TicketStatus target, String newOrderId, Instant newReservedUntil) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTicketTransitionException(id, status, target);
        }
        return new Ticket(id, eventId, target, newOrderId, newReservedUntil, version + 1);
    }

    // Security: prevents another order from confirming a ticket it does not hold
    private void requireHeldBy(String orderId) {
        if (!Objects.equals(this.orderId, orderId)) {
            throw new TicketNotOwnedException(id, orderId);
        }
    }
}
