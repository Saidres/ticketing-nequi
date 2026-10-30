package co.com.nequi.ticketing.model.ticket.gateways;

import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TicketRepository {

    Mono<Void> saveAll(List<Ticket> tickets);

    // All-or-nothing, conditional on each stored version being version - 1, else ConcurrencyConflictException
    Mono<Void> updateAll(List<Ticket> tickets);

    // Random order, so concurrent consumers do not compete for the same tickets
    Flux<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status, int limit);

    Flux<Ticket> findByOrderId(String orderId);

    Mono<Long> countByEventIdAndStatus(String eventId, TicketStatus status);
}
