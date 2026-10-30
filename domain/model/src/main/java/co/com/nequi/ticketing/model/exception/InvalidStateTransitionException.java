package co.com.nequi.ticketing.model.exception;

public final class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(String entity, String id, Enum<?> from, Enum<?> to) {
        super("%s %s cannot transition from %s to %s".formatted(entity, id, from, to));
    }
}
