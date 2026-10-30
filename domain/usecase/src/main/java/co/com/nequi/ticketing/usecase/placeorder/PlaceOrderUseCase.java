package co.com.nequi.ticketing.usecase.placeorder;

import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.order.gateways.OrderQueue;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

public class PlaceOrderUseCase {

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderQueue orderQueue;
    private final Clock clock;

    public PlaceOrderUseCase(EventRepository eventRepository, OrderRepository orderRepository,
                             OrderQueue orderQueue, Clock clock) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.orderQueue = orderQueue;
        this.clock = clock;
    }

    // Validates before any read, so invalid requests cost no database calls
    public Mono<Order> execute(PlaceOrderCommand command) {
        return Mono.fromCallable(() -> Order.pending(orderIdFor(command), command.eventId(),
                        command.customerId(), command.quantity(), clock.instant()))
                .flatMap(candidate -> findExisting(candidate.id())
                        .switchIfEmpty(Mono.defer(() -> create(candidate))));
    }

    private Mono<Order> create(Order candidate) {
        return eventRepository.findById(candidate.eventId())
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(candidate.eventId())))
                .then(Mono.defer(() -> orderRepository.create(candidate)))
                .flatMap(this::enqueue)
                .onErrorResume(ConcurrencyConflictException.class, duplicate -> findExisting(candidate.id()));
    }

    // A retry may find the order saved but never enqueued; the consumer tolerates duplicates
    private Mono<Order> findExisting(String orderId) {
        return orderRepository.findById(orderId)
                .flatMap(order -> order.status() == OrderStatus.PENDING ? enqueue(order) : Mono.just(order));
    }

    private Mono<Order> enqueue(Order order) {
        return orderQueue.publish(order.id()).thenReturn(order);
    }

    // Length prefix keeps the seed unambiguous, so two customers can never derive the same id
    private static String orderIdFor(PlaceOrderCommand command) {
        String seed = command.customerId().length() + ":" + command.customerId() + ":" + command.idempotencyKey();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
