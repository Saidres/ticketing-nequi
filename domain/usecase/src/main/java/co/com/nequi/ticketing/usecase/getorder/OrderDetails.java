package co.com.nequi.ticketing.usecase.getorder;

import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.ticket.Ticket;

import java.util.List;

public record OrderDetails(Order order, List<Ticket> tickets) {

    public OrderDetails {
        tickets = List.copyOf(tickets);
    }
}
