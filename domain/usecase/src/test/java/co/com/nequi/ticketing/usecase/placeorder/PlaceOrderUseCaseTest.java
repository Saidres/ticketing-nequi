package co.com.nequi.ticketing.usecase.placeorder;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.exception.IdempotencyKeyReusedException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.order.gateways.OrderQueue;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Event EVENT = new Event("E1", "Rock Fest", NOW.plus(Duration.ofDays(30)), "Movistar Arena", 100);
    private static final PlaceOrderCommand COMMAND = new PlaceOrderCommand("C1", "E1", 2, "key-1");

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderQueue orderQueue;

    private PlaceOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlaceOrderUseCase(eventRepository, orderRepository, orderQueue, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldCreatePendingOrderAndEnqueueIt() {
        when(orderRepository.findById(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById("E1")).thenReturn(Mono.just(EVENT));
        when(orderRepository.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(orderQueue.publish(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(COMMAND))
                .assertNext(order -> {
                    assertEquals(OrderStatus.PENDING, order.status());
                    assertEquals("E1", order.eventId());
                    assertEquals("C1", order.customerId());
                    assertEquals(2, order.quantity());
                    assertEquals(NOW, order.createdAt());
                    verify(orderQueue).publish(order.id());
                })
                .verifyComplete();
    }

    @Test
    void shouldDeriveTheSameOrderIdOnlyForTheSameCustomerAndKey() {
        String first = lookedUpOrderIdFor(COMMAND);
        String retried = lookedUpOrderIdFor(new PlaceOrderCommand("C1", "E1", 2, "key-1"));
        String otherCustomer = lookedUpOrderIdFor(new PlaceOrderCommand("C2", "E1", 2, "key-1"));
        String ambiguousSplit = lookedUpOrderIdFor(new PlaceOrderCommand("C1:key", "E1", 2, "1"));

        assertEquals(first, retried);
        assertNotEquals(first, otherCustomer);
        assertNotEquals(lookedUpOrderIdFor(new PlaceOrderCommand("C1", "E1", 2, "key:1")), ambiguousSplit);
    }

    @Test
    void shouldReturnExistingOrderWithoutCreatingAnother() {
        Order reserved = Order.pending("O1", "E1", "C1", 2, NOW).reserve(NOW, Duration.ofMinutes(10));
        when(orderRepository.findById(anyString())).thenReturn(Mono.just(reserved));

        StepVerifier.create(useCase.execute(COMMAND))
                .expectNext(reserved)
                .verifyComplete();

        verify(orderRepository, never()).create(any());
        verifyNoInteractions(orderQueue, eventRepository);
    }

    @Test
    void shouldRejectAKeyReusedForADifferentPurchase() {
        Order otherEvent = Order.pending("O1", "E2", "C1", 2, NOW);
        when(orderRepository.findById(anyString())).thenReturn(Mono.just(otherEvent));

        StepVerifier.create(useCase.execute(COMMAND)).expectError(IdempotencyKeyReusedException.class).verify();

        verifyNoInteractions(orderQueue, eventRepository);
    }

    @Test
    void shouldEnqueueAgainWhenExistingOrderIsStillPending() {
        Order pending = Order.pending("O1", "E1", "C1", 2, NOW);
        when(orderRepository.findById(anyString())).thenReturn(Mono.just(pending));
        when(orderQueue.publish("O1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(COMMAND))
                .expectNext(pending)
                .verifyComplete();

        verify(orderRepository, never()).create(any());
    }

    @Test
    void shouldPropagateFailureWhenReEnqueueingAPendingOrder() {
        when(orderRepository.findById(anyString())).thenReturn(Mono.just(Order.pending("O1", "E1", "C1", 2, NOW)));
        when(orderQueue.publish("O1")).thenReturn(Mono.error(new IllegalStateException("queue down")));

        StepVerifier.create(useCase.execute(COMMAND))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void shouldReturnAndEnqueueTheWinnerWhenTheSameRequestArrivesConcurrently() {
        Order winner = Order.pending("O1", "E1", "C1", 2, NOW);
        when(orderRepository.findById(anyString())).thenReturn(Mono.empty(), Mono.just(winner));
        when(eventRepository.findById("E1")).thenReturn(Mono.just(EVENT));
        when(orderRepository.create(any())).thenReturn(Mono.error(new ConcurrencyConflictException("Order", "O1")));
        when(orderQueue.publish("O1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(COMMAND))
                .expectNext(winner)
                .verifyComplete();
    }

    @Test
    void shouldFailWhenEventDoesNotExist() {
        when(orderRepository.findById(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById("E1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(COMMAND))
                .expectError(EventNotFoundException.class)
                .verify();

        verify(orderRepository, never()).create(any());
    }

    @Test
    void shouldRejectInvalidQuantityWithoutTouchingAnyRepository() {
        StepVerifier.create(useCase.execute(new PlaceOrderCommand("C1", "E1", 7, "key-1")))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(orderRepository, eventRepository, orderQueue);
    }

    @Test
    void shouldRejectMissingEventWithoutTouchingAnyRepository() {
        StepVerifier.create(useCase.execute(new PlaceOrderCommand("C1", " ", 2, "key-1")))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(orderRepository, eventRepository, orderQueue);
    }

    @Test
    void shouldPropagatePublishFailureSoTheClientCanRetry() {
        when(orderRepository.findById(anyString())).thenReturn(Mono.empty());
        when(eventRepository.findById("E1")).thenReturn(Mono.just(EVENT));
        when(orderRepository.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(orderQueue.publish(anyString())).thenReturn(Mono.error(new IllegalStateException("queue down")));

        StepVerifier.create(useCase.execute(COMMAND))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void shouldRequireCustomerAndIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceOrderCommand(" ", "E1", 2, "key-1"));
        assertThrows(IllegalArgumentException.class, () -> new PlaceOrderCommand("C1", "E1", 2, " "));
    }

    private String lookedUpOrderIdFor(PlaceOrderCommand command) {
        Order existing = Order.pending("O1", command.eventId(), command.customerId(), command.quantity(), NOW)
                .reserve(NOW, Duration.ofMinutes(10));
        when(orderRepository.findById(anyString())).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.execute(command)).expectNext(existing).verifyComplete();

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderRepository).findById(idCaptor.capture());
        clearInvocations(orderRepository);
        return idCaptor.getValue();
    }
}
