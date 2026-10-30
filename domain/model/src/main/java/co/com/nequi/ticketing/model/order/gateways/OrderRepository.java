package co.com.nequi.ticketing.model.order.gateways;

import co.com.nequi.ticketing.model.order.Order;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface OrderRepository {

    Mono<Order> save(Order order);

    // Conditional on the stored version being version - 1, else ConcurrencyConflictException
    Mono<Order> update(Order order);

    Mono<Order> findById(String id);

    Flux<Order> findExpiredReservations(Instant now);
}
