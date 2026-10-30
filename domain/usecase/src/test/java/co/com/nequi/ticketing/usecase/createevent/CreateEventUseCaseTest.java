package co.com.nequi.ticketing.usecase.createevent;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateEventUseCaseTest {

    private static final Instant DATE = Instant.parse("2026-12-01T20:00:00Z");
    private static final CreateEventCommand COMMAND = new CreateEventCommand("Rock Fest", DATE, "Movistar Arena", 3);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private CreateEventUseCase useCase;

    @Captor
    private ArgumentCaptor<List<Ticket>> ticketsCaptor;

    @Test
    void shouldCreateEventWithOneAvailableTicketPerSeat() {
        when(ticketRepository.saveAll(anyList())).thenReturn(Mono.empty());
        when(eventRepository.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(useCase.execute(COMMAND))
                .assertNext(event -> {
                    assertEquals("Rock Fest", event.name());
                    assertEquals(DATE, event.date());
                    assertEquals("Movistar Arena", event.venue());
                    assertEquals(3, event.totalCapacity());
                    assertInventoryBelongsTo(event);
                })
                .verifyComplete();
    }

    @Test
    void shouldNotTouchRepositoriesWhenEventIsInvalid() {
        StepVerifier.create(useCase.execute(new CreateEventCommand(" ", DATE, "Movistar Arena", 3)))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(eventRepository, ticketRepository);
    }

    @Test
    void shouldNotSaveEventWhenInventoryFails() {
        when(ticketRepository.saveAll(anyList())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.execute(COMMAND))
                .expectError(IllegalStateException.class)
                .verify();

        verify(eventRepository, never()).save(any());
    }

    @Test
    void shouldPropagateErrorWhenEventSaveFails() {
        when(ticketRepository.saveAll(anyList())).thenReturn(Mono.empty());
        when(eventRepository.save(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.execute(COMMAND))
                .expectError(IllegalStateException.class)
                .verify();
    }

    private void assertInventoryBelongsTo(Event event) {
        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        List<Ticket> tickets = ticketsCaptor.getValue();
        assertEquals(event.totalCapacity(), tickets.size());
        assertEquals(event.id() + "-000001", tickets.getFirst().id());
        assertEquals(event.id() + "-000003", tickets.getLast().id());
        assertTrue(tickets.stream().allMatch(ticket ->
                ticket.status() == TicketStatus.AVAILABLE && ticket.eventId().equals(event.id())));
    }
}
