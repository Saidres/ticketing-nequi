package co.com.nequi.ticketing.model.ticket.gateways;

import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TicketRepository {

    // Req. 1: creates the event inventory
    Flux<Ticket> saveAll(List<Ticket> tickets);

    // Req. 5: all-or-nothing conditional write; each stored ticket must still be at version - 1,
    // otherwise nothing is written and ConcurrencyConflictException is raised
    Mono<Void> updateAll(List<Ticket> tickets);

    // Candidates must come in random order so concurrent consumers do not all compete for the same tickets
    Flux<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status, int limit);

    Flux<Ticket> findByOrderId(String orderId);

    // Req. 7: real-time availability
    Mono<Long> countByEventIdAndStatus(String eventId, TicketStatus status);
}
