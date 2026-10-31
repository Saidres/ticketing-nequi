package co.com.nequi.ticketing.usecase.grantcomplimentary;

import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.exception.NotEnoughTicketsException;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import co.com.nequi.ticketing.usecase.shared.ConflictRetry;
import reactor.core.publisher.Mono;

import java.util.List;

// Complimentary tickets are final and never accounted as sales
public class GrantComplimentaryTicketsUseCase {

    public static final int MAX_PER_REQUEST = 10;

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public GrantComplimentaryTicketsUseCase(EventRepository eventRepository, TicketRepository ticketRepository) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
    }

    public Mono<List<Ticket>> execute(String eventId, int quantity) {
        return Mono.fromRunnable(() -> requireQuantityInRange(quantity))
                .then(Mono.defer(() -> eventRepository.findById(eventId)))
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)))
                .then(Mono.defer(() -> grant(eventId, quantity))
                        .retryWhen(ConflictRetry.withBackoff()));
    }

    // Each retry picks fresh candidates, so a ticket taken by a concurrent buyer is simply skipped
    private Mono<List<Ticket>> grant(String eventId, int quantity) {
        return ticketRepository.findByEventIdAndStatus(eventId, TicketStatus.AVAILABLE, quantity)
                .map(Ticket::grantComplimentary)
                .collectList()
                .flatMap(granted -> granted.size() < quantity
                        ? Mono.error(new NotEnoughTicketsException(eventId, quantity))
                        : ticketRepository.updateAll(granted).thenReturn(granted));
    }

    private static void requireQuantityInRange(int quantity) {
        if (quantity < 1 || quantity > MAX_PER_REQUEST) {
            throw new IllegalArgumentException("quantity must be between 1 and %d".formatted(MAX_PER_REQUEST));
        }
    }
}
