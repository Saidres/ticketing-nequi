package co.com.nequi.ticketing.model.exception;

// Another writer changed the data (lost conditional write or stale read); safe to retry
public final class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String entity, String id) {
        super("%s %s was modified concurrently".formatted(entity, id));
    }
}
