package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.dto.CreateEventRequest;
import co.com.nequi.ticketing.api.dto.EventResponse;
import co.com.nequi.ticketing.usecase.createevent.CreateEventUseCase;
import co.com.nequi.ticketing.usecase.getavailability.Availability;
import co.com.nequi.ticketing.usecase.getavailability.GetAvailabilityUseCase;
import co.com.nequi.ticketing.usecase.queryevents.QueryEventsUseCase;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

@Component
public class EventHandler {

    static final Duration AVAILABILITY_PUSH_INTERVAL = Duration.ofSeconds(2);
    static final Duration MAX_STREAM_DURATION = Duration.ofMinutes(10);

    private final CreateEventUseCase createEventUseCase;
    private final QueryEventsUseCase queryEventsUseCase;
    private final GetAvailabilityUseCase getAvailabilityUseCase;

    public EventHandler(CreateEventUseCase createEventUseCase, QueryEventsUseCase queryEventsUseCase,
                        GetAvailabilityUseCase getAvailabilityUseCase) {
        this.createEventUseCase = createEventUseCase;
        this.queryEventsUseCase = queryEventsUseCase;
        this.getAvailabilityUseCase = getAvailabilityUseCase;
    }

    public Mono<ServerResponse> createEvent(ServerRequest request) {
        return Requests.requiredBody(request, CreateEventRequest.class)
                .flatMap(body -> createEventUseCase.execute(body.toCommand()))
                .flatMap(event -> ServerResponse.created(URI.create("/api/v1/events/" + event.id()))
                        .bodyValue(EventResponse.of(event)))
                .onErrorResume(ApiErrors::toResponse);
    }

    public Mono<ServerResponse> listEvents(ServerRequest request) {
        return queryEventsUseCase.findAll()
                .map(EventResponse::of)
                .collectList()
                .flatMap(events -> ServerResponse.ok().bodyValue(events))
                .onErrorResume(ApiErrors::toResponse);
    }

    public Mono<ServerResponse> getEvent(ServerRequest request) {
        return queryEventsUseCase.findById(request.pathVariable("eventId"))
                .flatMap(event -> ServerResponse.ok().bodyValue(EventResponse.of(event)))
                .onErrorResume(ApiErrors::toResponse);
    }

    public Mono<ServerResponse> getAvailability(ServerRequest request) {
        return getAvailabilityUseCase.execute(request.pathVariable("eventId"))
                .flatMap(availability -> ServerResponse.ok().bodyValue(availability))
                .onErrorResume(ApiErrors::toResponse);
    }

    // Server-Sent Events: pushes the availability only when it changed. A failed tick is skipped instead of
    // closing the stream, and the stream ends after a while so idle clients cannot hold connections forever
    public Mono<ServerResponse> streamAvailability(ServerRequest request) {
        String eventId = request.pathVariable("eventId");
        return getAvailabilityUseCase.execute(eventId)
                .flatMap(first -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(Flux.interval(AVAILABILITY_PUSH_INTERVAL)
                                .onBackpressureDrop()
                                .concatMap(tick -> getAvailabilityUseCase.execute(eventId)
                                        .onErrorResume(error -> Mono.empty()))
                                .startWith(first)
                                .distinctUntilChanged()
                                .take(MAX_STREAM_DURATION), Availability.class))
                .onErrorResume(ApiErrors::toResponse);
    }
}
