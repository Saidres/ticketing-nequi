package co.com.nequi.ticketing.model.exception;

public final class TicketNotOwnedException extends BusinessException {

    public TicketNotOwnedException(String ticketId, String orderId) {
        super("Ticket %s is not held by order %s".formatted(ticketId, orderId));
    }
}
