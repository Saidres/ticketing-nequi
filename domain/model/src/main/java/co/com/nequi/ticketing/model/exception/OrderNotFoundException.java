package co.com.nequi.ticketing.model.exception;

// Also used when the order belongs to another customer, so ids cannot be probed
public final class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(String orderId) {
        super("Order %s does not exist".formatted(orderId));
    }
}
