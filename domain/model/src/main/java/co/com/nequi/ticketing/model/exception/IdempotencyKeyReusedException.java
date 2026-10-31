package co.com.nequi.ticketing.model.exception;

public final class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException(String orderId) {
        super("The idempotency key of order %s was already used for a different purchase".formatted(orderId));
    }
}
