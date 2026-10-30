package co.com.nequi.ticketing.model.exception;

// Never retried: the same input always breaks the same rule
public abstract sealed class BusinessException extends RuntimeException
        permits InvalidStateTransitionException, TicketNotOwnedException, ReservationExpiredException {

    protected BusinessException(String message) {
        super(message);
    }
}
