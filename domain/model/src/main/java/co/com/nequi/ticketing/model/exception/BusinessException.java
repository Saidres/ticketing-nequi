package co.com.nequi.ticketing.model.exception;

// Business rule violations: retrying will always produce the same result, so they must never be retried
public abstract sealed class BusinessException extends RuntimeException
        permits InvalidStateTransitionException, TicketNotOwnedException, ReservationExpiredException {

    protected BusinessException(String message) {
        super(message);
    }
}
