package co.com.nequi.ticketing.usecase.shared;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import reactor.util.retry.Retry;

import java.time.Duration;

// Only concurrency conflicts are worth retrying; business errors would fail the same way every time.
// After the last attempt the original conflict is propagated, not Reactor's RetryExhaustedException
public final class ConflictRetry {

    private static final int MAX_RETRIES = 3;
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(50);

    private ConflictRetry() {
    }

    public static Retry withBackoff() {
        return Retry.backoff(MAX_RETRIES, FIRST_BACKOFF)
                .filter(ConcurrencyConflictException.class::isInstance)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }
}
