package co.com.nequi.ticketing.usecase.getavailability;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailabilityUseCaseTest {

    private static final Event EVENT = new Event("E1", "Rock Fest", Instant.parse("2026-12-01T20:00:00Z"),
            "Movistar Arena", 100);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private GetAvailabilityUseCase useCase;

    @Test
    void shouldCountReservedAndPendingConfirmationTicketsAsReserved() {
        when(eventRepository.findById("E1")).thenReturn(Mono.just(EVENT));
        givenCount(TicketStatus.AVAILABLE, 80);
        givenCount(TicketStatus.RESERVED, 6);
        givenCount(TicketStatus.PENDING_CONFIRMATION, 4);
        givenCount(TicketStatus.SOLD, 8);
        givenCount(TicketStatus.COMPLIMENTARY, 2);

        StepVerifier.create(useCase.execute("E1"))
                .expectNext(new Availability("E1", 100, 80, 10, 8, 2))
                .verifyComplete();
    }

    @Test
    void shouldFailWhenEventDoesNotExist() {
        when(eventRepository.findById("E1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("E1"))
                .expectError(EventNotFoundException.class)
                .verify();

        verifyNoInteractions(ticketRepository);
    }

    private void givenCount(TicketStatus status, long count) {
        when(ticketRepository.countByEventIdAndStatus("E1", status)).thenReturn(Mono.just(count));
    }
}
