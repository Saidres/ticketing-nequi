package co.com.nequi.ticketing.usecase.createevent;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class CreateEventUseCase {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public CreateEventUseCase(EventRepository eventRepository, TicketRepository ticketRepository) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
    }

    // Inventory first: the event only becomes visible once all its tickets exist
    public Mono<Event> execute(CreateEventCommand command) {
        return Mono.fromCallable(() -> toEvent(command))
                .flatMap(event -> ticketRepository.saveAll(inventoryOf(event))
                        .then(Mono.defer(() -> eventRepository.save(event))));
    }

    private static Event toEvent(CreateEventCommand command) {
        return new Event(UUID.randomUUID().toString(), command.name(), command.date(),
                command.venue(), command.totalCapacity());
    }

    private static List<Ticket> inventoryOf(Event event) {
        return IntStream.rangeClosed(1, event.totalCapacity())
                .mapToObj(seat -> Ticket.available("%s-%06d".formatted(event.id(), seat), event.id()))
                .toList();
    }
}
