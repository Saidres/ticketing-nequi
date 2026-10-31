package co.com.nequi.ticketing.usecase.queryevents;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryEventsUseCaseTest {

    private static final Event EVENT = new Event("E1", "Rock Fest", Instant.parse("2026-12-01T20:00:00Z"),
            "Movistar Arena", 100);

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private QueryEventsUseCase useCase;

    @Test
    void shouldListAllEvents() {
        when(eventRepository.findAll()).thenReturn(Flux.just(EVENT));

        StepVerifier.create(useCase.findAll()).expectNext(EVENT).verifyComplete();
    }

    @Test
    void shouldFindAnExistingEvent() {
        when(eventRepository.findById("E1")).thenReturn(Mono.just(EVENT));

        StepVerifier.create(useCase.findById("E1")).expectNext(EVENT).verifyComplete();
    }

    @Test
    void shouldFailWhenEventDoesNotExist() {
        when(eventRepository.findById("E9")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.findById("E9")).expectError(EventNotFoundException.class).verify();
    }
}
