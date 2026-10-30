package co.com.nequi.ticketing.usecase.processorder;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import co.com.nequi.ticketing.model.order.gateways.OrderTicketsTransaction;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration LONGER_THAN_ALL_BACKOFFS = Duration.ofSeconds(5);
    private static final Order PENDING = Order.pending("O1", "E1", "C1", 2, NOW);
    private static final List<Ticket> CANDIDATES = List.of(Ticket.available("E1-000001", "E1"),
            Ticket.available("E1-000002", "E1"));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private OrderTicketsTransaction orderTicketsTransaction;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Captor
    private ArgumentCaptor<List<Ticket>> ticketsCaptor;

    private ProcessOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessOrderUseCase(orderRepository, ticketRepository, orderTicketsTransaction,
                new ReservationSettings(TTL), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldReserveOrderAndTicketsInOneTransaction() {
        givenPendingOrderWithCandidates(CANDIDATES);
        when(orderTicketsTransaction.commit(any(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("O1")).verifyComplete();

        verify(orderTicketsTransaction).commit(orderCaptor.capture(), ticketsCaptor.capture());
        assertEquals(OrderStatus.RESERVED, orderCaptor.getValue().status());
        assertEquals(NOW.plus(TTL), orderCaptor.getValue().reservedUntil());
        assertEquals(2, ticketsCaptor.getValue().size());
        assertTrue(ticketsCaptor.getValue().stream()
                .allMatch(ticket -> ticket.status() == TicketStatus.RESERVED && "O1".equals(ticket.orderId())));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void shouldRejectOrderWhenThereAreNotEnoughTickets(int available) {
        givenPendingOrderWithCandidates(CANDIDATES.subList(0, available));
        when(orderRepository.update(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(useCase.execute("O1")).verifyComplete();

        verify(orderRepository).update(orderCaptor.capture());
        assertEquals(OrderStatus.REJECTED, orderCaptor.getValue().status());
        verifyNoInteractions(orderTicketsTransaction);
    }

    @Test
    void shouldAcknowledgeMissingOrder() {
        when(orderRepository.findById("O1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("O1")).verifyComplete();

        verifyNoInteractions(ticketRepository, orderTicketsTransaction);
    }

    @Test
    void shouldAcknowledgeAlreadyProcessedOrder() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(PENDING.reserve(NOW, TTL)));

        StepVerifier.create(useCase.execute("O1")).verifyComplete();

        verifyNoInteractions(ticketRepository, orderTicketsTransaction);
    }

    @Test
    void shouldRetryWithFreshTicketsAfterAConcurrencyConflict() {
        givenPendingOrderWithCandidates(CANDIDATES);
        when(orderTicketsTransaction.commit(any(), anyList()))
                .thenReturn(Mono.error(new ConcurrencyConflictException("Ticket", "E1-000001")), Mono.empty());

        StepVerifier.withVirtualTime(() -> useCase.execute("O1"))
                .thenAwait(LONGER_THAN_ALL_BACKOFFS)
                .verifyComplete();

        verify(ticketRepository, times(2)).findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 2);
        verify(orderTicketsTransaction, times(2)).commit(any(), anyList());
    }

    @Test
    void shouldStopWhenAnotherConsumerAlreadyReservedTheOrder() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(PENDING), Mono.just(PENDING.reserve(NOW, TTL)));
        when(ticketRepository.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 2))
                .thenReturn(Flux.fromIterable(CANDIDATES));
        when(orderTicketsTransaction.commit(any(), anyList()))
                .thenReturn(Mono.error(new ConcurrencyConflictException("Order", "O1")));

        StepVerifier.withVirtualTime(() -> useCase.execute("O1"))
                .thenAwait(LONGER_THAN_ALL_BACKOFFS)
                .verifyComplete();

        verify(orderRepository, times(2)).findById("O1");
        verify(orderTicketsTransaction, times(1)).commit(any(), anyList());
    }

    @Test
    void shouldNotRejectTwiceWhenAnotherConsumerAlreadyRejectedTheOrder() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(PENDING), Mono.just(PENDING.reject()));
        when(ticketRepository.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 2)).thenReturn(Flux.empty());
        when(orderRepository.update(any())).thenReturn(Mono.error(new ConcurrencyConflictException("Order", "O1")));

        StepVerifier.withVirtualTime(() -> useCase.execute("O1"))
                .thenAwait(LONGER_THAN_ALL_BACKOFFS)
                .verifyComplete();

        verify(orderRepository, times(2)).findById("O1");
        verify(orderRepository, times(1)).update(any());
    }

    @Test
    void shouldPropagateConflictAfterRetriesSoTheQueueRedelivers() {
        givenPendingOrderWithCandidates(CANDIDATES);
        when(orderTicketsTransaction.commit(any(), anyList()))
                .thenReturn(Mono.error(new ConcurrencyConflictException("Ticket", "E1-000001")));

        StepVerifier.withVirtualTime(() -> useCase.execute("O1"))
                .thenAwait(LONGER_THAN_ALL_BACKOFFS)
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verify(orderTicketsTransaction, times(4)).commit(any(), anyList());
    }

    @Test
    void shouldNotRetryUnexpectedErrors() {
        givenPendingOrderWithCandidates(CANDIDATES);
        when(orderTicketsTransaction.commit(any(), anyList()))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.execute("O1"))
                .expectError(IllegalStateException.class)
                .verify();

        verify(orderTicketsTransaction, times(1)).commit(any(), anyList());
        verify(orderRepository, never()).update(any());
    }

    @Test
    void shouldRejectInvalidTtlAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationSettings(null));
        assertThrows(IllegalArgumentException.class, () -> new ReservationSettings(Duration.ofMinutes(15)));
    }

    private void givenPendingOrderWithCandidates(List<Ticket> candidates) {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(PENDING));
        when(ticketRepository.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 2))
                .thenReturn(Flux.fromIterable(candidates));
    }
}
