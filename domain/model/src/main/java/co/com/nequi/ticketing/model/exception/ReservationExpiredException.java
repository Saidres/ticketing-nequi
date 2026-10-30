package co.com.nequi.ticketing.model.exception;

public final class ReservationExpiredException extends BusinessException {

    public ReservationExpiredException(String orderId) {
        super("Order %s reservation has expired".formatted(orderId));
    }
}
