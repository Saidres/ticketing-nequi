package co.com.nequi.ticketing.usecase.queryevents;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class QueryEventsUseCase {

    private final EventRepository eventRepository;

    public QueryEventsUseCase(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Flux<Event> findAll() {
        return eventRepository.findAll();
    }

    public Mono<Event> findById(String eventId) {
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)));
    }
}
