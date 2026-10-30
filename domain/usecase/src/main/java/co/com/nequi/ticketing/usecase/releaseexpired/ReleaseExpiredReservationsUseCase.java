package co.com.nequi.ticketing.usecase.releaseexpired;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import co.com.nequi.ticketing.model.order.gateways.OrderTicketsTransaction;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

public class ReleaseExpiredReservationsUseCase {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final OrderTicketsTransaction orderTicketsTransaction;
    private final Clock clock;

    public ReleaseExpiredReservationsUseCase(OrderRepository orderRepository, TicketRepository ticketRepository,
                                             OrderTicketsTransaction orderTicketsTransaction, Clock clock) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.orderTicketsTransaction = orderTicketsTransaction;
        this.clock = clock;
    }

    // Returns how many orders were expired; a conflict means the customer confirmed first (or the read was stale),
    // so the order is skipped and the next run looks at it again
    public Mono<Long> execute() {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            return orderRepository.findExpiredReservations(now)
                    .filter(order -> order.isReservationExpired(now))
                    .concatMap(order -> release(order)
                            .thenReturn(order)
                            .onErrorResume(ConcurrencyConflictException.class, changedMeanwhile -> Mono.empty()))
                    .count();
        });
    }

    private Mono<Void> release(Order order) {
        return ticketRepository.findByOrderId(order.id())
                .collectList()
                .map(tickets -> order.requireOwnTickets(tickets, TicketStatus.RESERVED))
                .map(tickets -> tickets.stream().map(ticket -> ticket.release(order.id())).toList())
                .flatMap(released -> orderTicketsTransaction.commit(order.expire(), released));
    }
}
