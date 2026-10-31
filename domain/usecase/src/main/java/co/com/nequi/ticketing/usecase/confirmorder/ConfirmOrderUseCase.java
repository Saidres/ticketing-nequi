package co.com.nequi.ticketing.usecase.confirmorder;

import co.com.nequi.ticketing.model.exception.OrderNotFoundException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import co.com.nequi.ticketing.model.order.gateways.OrderTicketsTransaction;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import co.com.nequi.ticketing.usecase.shared.OrderDetails;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.function.UnaryOperator;

public class ConfirmOrderUseCase {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final OrderTicketsTransaction orderTicketsTransaction;
    private final Clock clock;

    public ConfirmOrderUseCase(OrderRepository orderRepository, TicketRepository ticketRepository,
                               OrderTicketsTransaction orderTicketsTransaction, Clock clock) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.orderTicketsTransaction = orderTicketsTransaction;
        this.clock = clock;
    }

    public Mono<OrderDetails> execute(String orderId, String customerId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.isOwnedBy(customerId))
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)))
                .flatMap(this::advance);
    }

    // Idempotent and resumable: a retry after a crash between the two commits finishes the sale
    private Mono<OrderDetails> advance(Order order) {
        return switch (order.status()) {
            case CONFIRMED -> ownTickets(order, TicketStatus.SOLD).map(tickets -> new OrderDetails(order, tickets));
            case CONFIRMING -> ownTickets(order, TicketStatus.PENDING_CONFIRMATION)
                    .flatMap(tickets -> sell(new OrderDetails(order, tickets)));
            default -> startConfirmation(order).flatMap(this::sell);
        };
    }

    private Mono<OrderDetails> startConfirmation(Order reserved) {
        return Mono.fromCallable(() -> reserved.startConfirmation(clock.instant()))
                .flatMap(confirming -> ownTickets(reserved, TicketStatus.RESERVED)
                        .flatMap(tickets -> commit(confirming,
                                transition(tickets, ticket -> ticket.startConfirmation(reserved.id())))));
    }

    private Mono<OrderDetails> sell(OrderDetails confirming) {
        return Mono.fromCallable(() -> confirming.order().confirm())
                .flatMap(confirmed -> commit(confirmed,
                        transition(confirming.tickets(), ticket -> ticket.sell(confirmed.id()))));
    }

    private Mono<List<Ticket>> ownTickets(Order order, TicketStatus expected) {
        return ticketRepository.findByOrderId(order.id())
                .collectList()
                .map(tickets -> order.requireOwnTickets(tickets, expected));
    }

    private Mono<OrderDetails> commit(Order order, List<Ticket> tickets) {
        return orderTicketsTransaction.commit(order, tickets).thenReturn(new OrderDetails(order, tickets));
    }

    private static List<Ticket> transition(List<Ticket> tickets, UnaryOperator<Ticket> change) {
        return tickets.stream().map(change).toList();
    }
}
