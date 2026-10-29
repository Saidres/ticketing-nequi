package co.com.nequi.ticketing.model.order.gateways;

import co.com.nequi.ticketing.model.order.Order;
import reactor.core.publisher.Mono;

public interface OrderRepository {

    Mono<Order> save(Order order);

    Mono<Order> findById(String id);
}
