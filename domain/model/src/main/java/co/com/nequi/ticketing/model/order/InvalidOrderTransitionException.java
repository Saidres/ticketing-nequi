package co.com.nequi.ticketing.model.order;

public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException(String orderId, OrderStatus from, OrderStatus to) {
        super("Order %s cannot transition from %s to %s".formatted(orderId, from, to));
    }
}
