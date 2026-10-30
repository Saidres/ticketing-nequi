package co.com.nequi.ticketing.model.order.gateways;

import co.com.nequi.ticketing.model.order.Order;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface OrderRepository {

    // Creates a new order; fails if the id already exists
    Mono<Order> save(Order order);

    // Req. 5: conditional write; the stored order must still be at version - 1,
    // otherwise ConcurrencyConflictException is raised
    Mono<Order> update(Order order);

    // Req. 4: order status lookup
    Mono<Order> findById(String id);

    // Req. 6: reserved orders past their time limit
    Flux<Order> findExpiredReservations(Instant now);
}
