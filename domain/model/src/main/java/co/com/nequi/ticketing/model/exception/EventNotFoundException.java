package co.com.nequi.ticketing.model.exception;

public final class EventNotFoundException extends BusinessException {

    public EventNotFoundException(String eventId) {
        super("Event %s does not exist".formatted(eventId));
    }
}
