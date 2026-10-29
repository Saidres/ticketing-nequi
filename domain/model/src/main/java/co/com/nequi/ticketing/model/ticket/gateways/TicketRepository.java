package co.com.nequi.ticketing.model.ticket.gateways;

import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface TicketRepository {

    Flux<Ticket> saveAll(List<Ticket> tickets);

    Mono<Ticket> update(Ticket previous, Ticket updated);

    Flux<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status, int limit);

    Flux<Ticket> findByOrderId(String orderId);

    Mono<Long> countByEventIdAndStatus(String eventId, TicketStatus status);

    Flux<Ticket> findExpiredReservations(Instant now);
}
