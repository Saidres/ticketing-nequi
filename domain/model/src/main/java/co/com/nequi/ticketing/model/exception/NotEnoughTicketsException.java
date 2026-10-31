package co.com.nequi.ticketing.model.exception;

public final class NotEnoughTicketsException extends BusinessException {

    public NotEnoughTicketsException(String eventId, int requested) {
        super("Event %s does not have %d available tickets".formatted(eventId, requested));
    }
}
