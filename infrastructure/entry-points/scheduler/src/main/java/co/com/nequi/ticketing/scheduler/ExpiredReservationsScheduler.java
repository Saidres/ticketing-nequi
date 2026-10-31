package co.com.nequi.ticketing.scheduler;

import co.com.nequi.ticketing.usecase.releaseexpired.ReleaseExpiredReservationsUseCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

// Req. 6: periodically returns expired reservations to the inventory; a failed run is retried on the next tick
@Component
public class ExpiredReservationsScheduler implements SmartLifecycle {

    private static final Logger log = LogManager.getLogger(ExpiredReservationsScheduler.class);

    private final ReleaseExpiredReservationsUseCase releaseExpiredReservations;
    private final Duration interval;
    private final Disposable.Swap running = Disposables.swap();

    public ExpiredReservationsScheduler(ReleaseExpiredReservationsUseCase releaseExpiredReservations,
                                        @Value("${ticketing.expiration-sweep-interval}") Duration interval) {
        this.releaseExpiredReservations = releaseExpiredReservations;
        this.interval = interval;
    }

    Flux<Long> sweeps() {
        return Flux.interval(interval)
                .onBackpressureDrop()
                .concatMap(tick -> releaseExpiredReservations.execute()
                        .onErrorResume(error -> {
                            log.error("Expired reservations sweep failed", error);
                            return Mono.empty();
                        }));
    }

    @Override
    public void start() {
        running.update(sweeps()
                .filter(released -> released > 0)
                .subscribe(released -> log.info("Released {} expired reservations", released),
                        error -> log.error("Expired reservations scheduler stopped", error)));
    }

    @Override
    public void stop() {
        running.update(Disposables.disposed());
    }

    @Override
    public boolean isRunning() {
        Disposable current = running.get();
        return current != null && !current.isDisposed();
    }
}
