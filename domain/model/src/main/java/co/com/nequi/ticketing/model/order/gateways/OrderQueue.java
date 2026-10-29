package co.com.nequi.ticketing.model.order.gateways;

import reactor.core.publisher.Mono;

public interface OrderQueue {

    Mono<Void> publish(String orderId);
}
