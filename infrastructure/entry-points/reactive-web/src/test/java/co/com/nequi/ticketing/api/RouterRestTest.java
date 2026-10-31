package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.config.RateLimitConfig;
import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.OrderNotFoundException;
import co.com.nequi.ticketing.model.exception.ReservationExpiredException;
import co.com.nequi.ticketing.model.exception.TicketNotOwnedException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.usecase.confirmorder.ConfirmOrderUseCase;
import co.com.nequi.ticketing.usecase.createevent.CreateEventUseCase;
import co.com.nequi.ticketing.usecase.getavailability.Availability;
import co.com.nequi.ticketing.usecase.getavailability.GetAvailabilityUseCase;
import co.com.nequi.ticketing.usecase.getorder.GetOrderUseCase;
import co.com.nequi.ticketing.usecase.getorder.OrderDetails;
import co.com.nequi.ticketing.usecase.placeorder.PlaceOrderCommand;
import co.com.nequi.ticketing.usecase.placeorder.PlaceOrderUseCase;
import co.com.nequi.ticketing.usecase.queryevents.QueryEventsUseCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouterRestTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Event EVENT = new Event("E1", "Rock Fest", NOW.plus(Duration.ofDays(30)), "Movistar Arena", 100);
    private static final Order PENDING = Order.pending("O1", "E1", "C1", 2, NOW);
    private static final Availability AVAILABILITY = new Availability("E1", 100, 90, 6, 4, 0);

    @Mock
    private CreateEventUseCase createEventUseCase;
    @Mock
    private QueryEventsUseCase queryEventsUseCase;
    @Mock
    private GetAvailabilityUseCase getAvailabilityUseCase;
    @Mock
    private PlaceOrderUseCase placeOrderUseCase;
    @Mock
    private GetOrderUseCase getOrderUseCase;
    @Mock
    private ConfirmOrderUseCase confirmOrderUseCase;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        EventHandler events = new EventHandler(createEventUseCase, queryEventsUseCase, getAvailabilityUseCase);
        OrderHandler orders = new OrderHandler(placeOrderUseCase, getOrderUseCase, confirmOrderUseCase,
                new RateLimitConfig().rateLimiterRegistry(2));
        client = WebTestClient.bindToRouterFunction(new RouterRest().routerFunction(events, orders)).build();
    }

    @Test
    void shouldCreateAnEvent() {
        when(createEventUseCase.execute(any())).thenReturn(Mono.just(EVENT));

        client.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Rock Fest", "date", "2026-12-01T10:00:00Z", "venue", "Movistar Arena",
                        "totalCapacity", 100))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/api/v1/events/E1")
                .expectBody().jsonPath("$.eventId").isEqualTo("E1");
    }

    @Test
    void shouldAnswerBadRequestWhenTheEventIsInvalid() {
        when(createEventUseCase.execute(any())).thenReturn(Mono.error(new IllegalArgumentException("name is required")));

        client.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("venue", "Movistar Arena", "totalCapacity", 100))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("name is required");
    }

    @Test
    void shouldAnswerBadRequestWithoutBody() {
        client.post().uri("/api/v1/events").contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldAnswerBadRequestForMalformedJson() {
        client.post().uri("/api/v1/events").contentType(MediaType.APPLICATION_JSON).bodyValue("{not json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldAnswerUnsupportedMediaTypeWithoutJsonContentType() {
        client.post().uri("/api/v1/events").contentType(MediaType.TEXT_PLAIN).bodyValue("hello")
                .exchange()
                .expectStatus().isEqualTo(415);
    }

    @Test
    void shouldListEvents() {
        when(queryEventsUseCase.findAll()).thenReturn(Flux.just(EVENT));

        client.get().uri("/api/v1/events").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].name").isEqualTo("Rock Fest");
    }

    @Test
    void shouldGetAnEvent() {
        when(queryEventsUseCase.findById("E1")).thenReturn(Mono.just(EVENT));

        client.get().uri("/api/v1/events/E1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.venue").isEqualTo("Movistar Arena");
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownEvent() {
        when(queryEventsUseCase.findById("E9")).thenReturn(Mono.error(new EventNotFoundException("E9")));

        client.get().uri("/api/v1/events/E9").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void shouldReturnTheAvailability() {
        when(getAvailabilityUseCase.execute("E1")).thenReturn(Mono.just(AVAILABILITY));

        client.get().uri("/api/v1/events/E1/availability").exchange()
                .expectStatus().isOk()
                .expectBody(Availability.class).isEqualTo(AVAILABILITY);
    }

    @Test
    void shouldStreamTheAvailabilityAsServerSentEvents() {
        when(getAvailabilityUseCase.execute("E1")).thenReturn(Mono.just(AVAILABILITY));

        Flux<Availability> stream = client.get().uri("/api/v1/events/E1/availability/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(Availability.class)
                .getResponseBody();

        StepVerifier.create(stream.take(1)).expectNext(AVAILABILITY).verifyComplete();
    }

    @Test
    void shouldAnswerNotFoundWhenStreamingAnUnknownEvent() {
        when(getAvailabilityUseCase.execute("E9")).thenReturn(Mono.error(new EventNotFoundException("E9")));

        client.get().uri("/api/v1/events/E9/availability/stream").exchange().expectStatus().isNotFound();
    }

    @Test
    void shouldAcceptAPurchaseAndPointToTheOrder() {
        when(placeOrderUseCase.execute(new PlaceOrderCommand("C1", "E1", 2, "key-1"))).thenReturn(Mono.just(PENDING));

        client.post().uri("/api/v1/orders")
                .header(Requests.CUSTOMER_ID, "C1")
                .header(Requests.IDEMPOTENCY_KEY, "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("eventId", "E1", "quantity", 2))
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().location("/api/v1/orders/O1")
                .expectBody().jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void shouldAnswerTooManyRequestsWhenACustomerExceedsTheRateLimit() {
        when(placeOrderUseCase.execute(any())).thenReturn(Mono.just(PENDING));

        for (int attempt = 1; attempt <= 2; attempt++) {
            purchaseBy("greedy", "key-" + attempt).expectStatus().isAccepted();
        }
        purchaseBy("greedy", "key-3").expectStatus().isEqualTo(429);
        purchaseBy("someone-else", "key-1").expectStatus().isAccepted();
    }

    @Test
    void shouldAnswerServiceUnavailableWhileTheQueueCircuitIsOpen() {
        when(placeOrderUseCase.execute(any())).thenReturn(Mono.error(
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("sqs-sender"))));

        purchaseBy("C1", "key-1").expectStatus().isEqualTo(503);
    }

    private WebTestClient.ResponseSpec purchaseBy(String customerId, String idempotencyKey) {
        return client.post().uri("/api/v1/orders")
                .header(Requests.CUSTOMER_ID, customerId)
                .header(Requests.IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("eventId", "E1", "quantity", 2))
                .exchange();
    }

    @Test
    void shouldRequireTheIdempotencyKey() {
        client.post().uri("/api/v1/orders")
                .header(Requests.CUSTOMER_ID, "C1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("eventId", "E1", "quantity", 2))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Idempotency-Key header is required");

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    void shouldReturnTheOrderWithTheStatusOfEachTicket() {
        Order reserved = PENDING.reserve(NOW, Duration.ofMinutes(10));
        Ticket held = Ticket.available("E1-000001", "E1").reserve("O1");
        when(getOrderUseCase.execute("O1", "C1")).thenReturn(Mono.just(new OrderDetails(reserved, List.of(held))));

        client.get().uri("/api/v1/orders/O1").header(Requests.CUSTOMER_ID, "C1").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("RESERVED")
                .jsonPath("$.tickets[0].ticketId").isEqualTo("E1-000001")
                .jsonPath("$.tickets[0].status").isEqualTo("RESERVED");
    }

    @Test
    void shouldRequireTheCustomerToReadAnOrder() {
        client.get().uri("/api/v1/orders/O1").exchange().expectStatus().isBadRequest();

        verifyNoInteractions(getOrderUseCase);
    }

    @Test
    void shouldAnswerNotFoundForAnOrderOfAnotherCustomer() {
        when(getOrderUseCase.execute("O1", "C2")).thenReturn(Mono.error(new OrderNotFoundException("O1")));

        client.get().uri("/api/v1/orders/O1").header(Requests.CUSTOMER_ID, "C2").exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldConfirmAnOrder() {
        Order confirmed = PENDING.reserve(NOW, Duration.ofMinutes(10)).startConfirmation(NOW).confirm();
        when(confirmOrderUseCase.execute("O1", "C1")).thenReturn(Mono.just(confirmed));

        client.post().uri("/api/v1/orders/O1/confirm").header(Requests.CUSTOMER_ID, "C1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void shouldAnswerConflictForBusinessAndConcurrencyConflicts() {
        when(confirmOrderUseCase.execute("O1", "C1")).thenReturn(Mono.error(new ReservationExpiredException("O1")));
        when(confirmOrderUseCase.execute("O2", "C1")).thenReturn(Mono.error(new ConcurrencyConflictException("Order", "O2")));
        when(confirmOrderUseCase.execute("O3", "C1")).thenReturn(Mono.error(
                new InvalidStateTransitionException("Order", "O3", OrderStatus.EXPIRED, OrderStatus.CONFIRMING)));
        when(confirmOrderUseCase.execute("O4", "C1")).thenReturn(Mono.error(new TicketNotOwnedException("T1", "O4")));

        client.post().uri("/api/v1/orders/O1/confirm").header(Requests.CUSTOMER_ID, "C1").exchange()
                .expectStatus().isEqualTo(409);
        client.post().uri("/api/v1/orders/O2/confirm").header(Requests.CUSTOMER_ID, "C1").exchange()
                .expectStatus().isEqualTo(409);
        client.post().uri("/api/v1/orders/O3/confirm").header(Requests.CUSTOMER_ID, "C1").exchange()
                .expectStatus().isEqualTo(409);
        client.post().uri("/api/v1/orders/O4/confirm").header(Requests.CUSTOMER_ID, "C1").exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void shouldHideTheDetailsOfUnexpectedErrors() {
        when(queryEventsUseCase.findById("E1")).thenReturn(Mono.error(new IllegalStateException("db password wrong")));

        client.get().uri("/api/v1/events/E1").exchange()
                .expectStatus().is5xxServerError()
                .expectBody().jsonPath("$.message").isEqualTo("Unexpected error");
    }
}
