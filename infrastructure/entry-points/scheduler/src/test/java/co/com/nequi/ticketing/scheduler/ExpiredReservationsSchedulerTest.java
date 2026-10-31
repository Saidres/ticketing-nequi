package co.com.nequi.ticketing.scheduler;

import co.com.nequi.ticketing.usecase.releaseexpired.ReleaseExpiredReservationsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredReservationsSchedulerTest {

    private static final Duration INTERVAL = Duration.ofSeconds(30);

    @Mock
    private ReleaseExpiredReservationsUseCase useCase;

    @Test
    void shouldReleaseExpiredReservationsOnEveryTick() {
        when(useCase.execute()).thenReturn(Mono.just(2L));
        ExpiredReservationsScheduler scheduler = new ExpiredReservationsScheduler(useCase, INTERVAL);

        StepVerifier.withVirtualTime(() -> scheduler.sweeps().take(2))
                .thenAwait(INTERVAL.multipliedBy(2))
                .expectNext(2L, 2L)
                .verifyComplete();
    }

    @Test
    void shouldKeepRunningAfterAFailedSweep() {
        when(useCase.execute()).thenReturn(Mono.error(new IllegalStateException("db down")), Mono.just(1L));
        ExpiredReservationsScheduler scheduler = new ExpiredReservationsScheduler(useCase, INTERVAL);

        StepVerifier.withVirtualTime(() -> scheduler.sweeps().take(1))
                .thenAwait(INTERVAL.multipliedBy(2))
                .expectNext(1L)
                .verifyComplete();

        verify(useCase, times(2)).execute();
    }

    @Test
    void shouldStartAndStopWithTheApplication() {
        ExpiredReservationsScheduler scheduler = new ExpiredReservationsScheduler(useCase, INTERVAL);

        scheduler.start();
        assertTrue(scheduler.isRunning());
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }
}
