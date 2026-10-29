package co.com.nequi.ticketing.model.ticket.gateways;

public class TicketConcurrencyException extends RuntimeException {

    public TicketConcurrencyException(String ticketId) {
        super("Ticket %s was modified concurrently".formatted(ticketId));
    }
}
