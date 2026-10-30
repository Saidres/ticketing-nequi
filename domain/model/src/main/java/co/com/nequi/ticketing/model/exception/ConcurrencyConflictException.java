package co.com.nequi.ticketing.model.exception;

// Thrown by adapters when a conditional write loses a race; safe to retry
public final class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String entity, String id) {
        super("%s %s was modified concurrently".formatted(entity, id));
    }
}
