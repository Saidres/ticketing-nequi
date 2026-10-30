package co.com.nequi.ticketing.usecase.getorder;

import co.com.nequi.ticketing.model.exception.OrderNotFoundException;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import reactor.core.publisher.Mono;

public class GetOrderUseCase {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;

    public GetOrderUseCase(OrderRepository orderRepository, TicketRepository ticketRepository) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
    }

    public Mono<OrderDetails> execute(String orderId, String customerId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.isOwnedBy(customerId))
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)))
                .flatMap(order -> ticketRepository.findByOrderId(order.id())
                        .collectList()
                        .map(tickets -> new OrderDetails(order, tickets)));
    }
}
