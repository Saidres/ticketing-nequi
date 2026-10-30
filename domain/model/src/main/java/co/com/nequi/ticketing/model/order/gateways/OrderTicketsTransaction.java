package co.com.nequi.ticketing.model.order.gateways;

import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.ticket.Ticket;
import reactor.core.publisher.Mono;

import java.util.List;

public interface OrderTicketsTransaction {

    // Writes an order and its tickets together, all-or-nothing; every item is conditional
    // on its stored version being version - 1, else ConcurrencyConflictException
    Mono<Void> commit(Order order, List<Ticket> tickets);
}
