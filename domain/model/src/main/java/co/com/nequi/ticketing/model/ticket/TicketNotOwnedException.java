package co.com.nequi.ticketing.model.ticket;

public class TicketNotOwnedException extends RuntimeException {
    public TicketNotOwnedException(String ticketId, String orderId) {
        super("Ticket %s is not held by order %s".formatted(ticketId, orderId));
    }
}
