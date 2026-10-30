package co.com.nequi.ticketing.usecase.getavailability;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import reactor.core.publisher.Mono;

public class GetAvailabilityUseCase {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public GetAvailabilityUseCase(EventRepository eventRepository, TicketRepository ticketRepository) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
    }

    public Mono<Availability> execute(String eventId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)))
                .flatMap(this::countTickets);
    }

    private Mono<Availability> countTickets(Event event) {
        return Mono.zip(count(event, TicketStatus.AVAILABLE), count(event, TicketStatus.RESERVED),
                        count(event, TicketStatus.PENDING_CONFIRMATION), count(event, TicketStatus.SOLD),
                        count(event, TicketStatus.COMPLIMENTARY))
                .map(counts -> new Availability(event.id(), event.totalCapacity(), counts.getT1(),
                        counts.getT2() + counts.getT3(), counts.getT4(), counts.getT5()));
    }

    private Mono<Long> count(Event event, TicketStatus status) {
        return ticketRepository.countByEventIdAndStatus(event.id(), status);
    }
}
