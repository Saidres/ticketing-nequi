package co.com.nequi.ticketing.usecase.processorder;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import co.com.nequi.ticketing.model.order.gateways.OrderTicketsTransaction;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public class ProcessOrderUseCase {

    private static final int MAX_RETRIES = 3;
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(50);

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final OrderTicketsTransaction orderTicketsTransaction;
    private final ReservationSettings settings;
    private final Clock clock;

    public ProcessOrderUseCase(OrderRepository orderRepository, TicketRepository ticketRepository,
                               OrderTicketsTransaction orderTicketsTransaction, ReservationSettings settings,
                               Clock clock) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.orderTicketsTransaction = orderTicketsTransaction;
        this.settings = settings;
        this.clock = clock;
    }

    // At-least-once delivery: a missing or already processed order is acknowledged without changes.
    // Each retry re-reads the order, so a duplicate consumer that already won simply stops here.
    public Mono<Void> execute(String orderId) {
        return Mono.defer(() -> orderRepository.findById(orderId)
                        .filter(order -> order.status() == OrderStatus.PENDING)
                        .flatMap(this::reserveOrReject))
                .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_BACKOFF)
                        .filter(ConcurrencyConflictException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private Mono<Void> reserveOrReject(Order order) {
        return ticketRepository.findByEventIdAndStatus(order.eventId(), TicketStatus.AVAILABLE, order.quantity())
                .collectList()
                .flatMap(candidates -> candidates.size() < order.quantity()
                        ? orderRepository.update(order.reject()).then()
                        : orderTicketsTransaction.commit(order.reserve(clock.instant(), settings.ttl()),
                        heldBy(order, candidates)));
    }

    private static List<Ticket> heldBy(Order order, List<Ticket> candidates) {
        return candidates.stream().map(ticket -> ticket.reserve(order.id())).toList();
    }
}
