package co.com.nequi.ticketing.usecase.grantcomplimentary;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.exception.NotEnoughTicketsException;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrantComplimentaryTicketsUseCaseTest {

    private static final Event EVENT = new Event("E1", "Rock Fest", Instant.parse("2026-12-01T20:00:00Z"),
            "Movistar Arena", 100);
    private static final List<Ticket> AVAILABLE = List.of(Ticket.available("E1-000001", "E1"),
            Ticket.available("E1-000002", "E1"));

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private GrantComplimentaryTicketsUseCase useCase;

    @Captor
    private ArgumentCaptor<List<Ticket>> ticketsCaptor;

    @Test
    void shouldTurnAvailableTicketsIntoComplimentaryAtomically() {
        givenAvailable(AVAILABLE);
        when(ticketRepository.updateAll(anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("E1", 2))
                .assertNext(granted -> assertTrue(granted.stream()
                        .allMatch(ticket -> ticket.status() == TicketStatus.COMPLIMENTARY && ticket.orderId() == null)))
                .verifyComplete();

        verify(ticketRepository).updateAll(ticketsCaptor.capture());
        assertTrue(ticketsCaptor.getValue().stream().allMatch(ticket -> ticket.status() == TicketStatus.COMPLIMENTARY));
    }

    @Test
    void shouldFailWhenThereAreNotEnoughAvailableTickets() {
        givenAvailable(AVAILABLE.subList(0, 1));

        StepVerifier.create(useCase.execute("E1", 2)).expectError(NotEnoughTicketsException.class).verify();

        verify(ticketRepository, never()).updateAll(anyList());
    }

    @Test
    void shouldRetryWithFreshTicketsAfterAConcurrencyConflict() {
        givenAvailable(AVAILABLE);
        when(ticketRepository.updateAll(anyList()))
                .thenReturn(Mono.error(new ConcurrencyConflictException("Ticket", "E1-000001")), Mono.empty());

        StepVerifier.withVirtualTime(() -> useCase.execute("E1", 2))
                .thenAwait(Duration.ofSeconds(5))
                .expectNextCount(1)
                .verifyComplete();

        verify(ticketRepository, times(2)).findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 2);
    }

    @Test
    void shouldFailWhenTheEventDoesNotExist() {
        when(eventRepository.findById("E9")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("E9", 1)).expectError(EventNotFoundException.class).verify();

        verifyNoInteractions(ticketRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, GrantComplimentaryTicketsUseCase.MAX_PER_REQUEST + 1})
    void shouldRejectQuantitiesOutOfRangeWithoutTouchingTheRepositories(int quantity) {
        StepVerifier.create(useCase.execute("E1", quantity)).expectError(IllegalArgumentException.class).verify();

        verifyNoInteractions(eventRepository, ticketRepository);
    }

    private void givenAvailable(List<Ticket> tickets) {
        when(eventRepository.findById("E1")).thenReturn(Mono.just(EVENT));
        when(ticketRepository.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 2)).thenReturn(Flux.fromIterable(tickets));
    }
}
