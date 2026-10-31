package co.com.nequi.ticketing.api.dto;

import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;

public record TicketView(String ticketId, TicketStatus status) {

    public static TicketView of(Ticket ticket) {
        return new TicketView(ticket.id(), ticket.status());
    }
}
