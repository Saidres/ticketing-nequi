package co.com.nequi.ticketing.model.ticket;

public class InvalidTicketTransitionException extends RuntimeException {
    public InvalidTicketTransitionException(String ticketId, TicketStatus from, TicketStatus to) {
        super("Ticket %s cannot transition from %s to %s".formatted(ticketId, from, to));
    }
}
