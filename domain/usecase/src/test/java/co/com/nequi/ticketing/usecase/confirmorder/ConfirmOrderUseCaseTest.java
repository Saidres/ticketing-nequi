package co.com.nequi.ticketing.usecase.confirmorder;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.OrderNotFoundException;
import co.com.nequi.ticketing.model.exception.ReservationExpiredException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Order RESERVED = Order.pending("O1", "E1", "C1", 2, NOW).reserve(NOW, TTL);
    private static final List<Ticket> RESERVED_TICKETS = List.of(
            Ticket.available("E1-000001", "E1").reserve("O1"),
            Ticket.available("E1-000002", "E1").reserve("O1"));

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

    private ConfirmOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = useCaseAt(NOW);
    }

    @Test
    void shouldConfirmOrderAndSellItsTicketsInTwoAtomicSteps() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.fromIterable(RESERVED_TICKETS));
        when(orderTicketsTransaction.commit(any(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("O1", "C1"))
                .assertNext(order -> assertEquals(OrderStatus.CONFIRMED, order.status()))
                .verifyComplete();

        verify(ticketRepository, times(1)).findByOrderId("O1");
        verify(orderTicketsTransaction, times(2)).commit(orderCaptor.capture(), ticketsCaptor.capture());
        assertEquals(OrderStatus.CONFIRMING, orderCaptor.getAllValues().getFirst().status());
        assertAllTickets(ticketsCaptor.getAllValues().getFirst(), TicketStatus.PENDING_CONFIRMATION);
        assertEquals(OrderStatus.CONFIRMED, orderCaptor.getAllValues().getLast().status());
        assertAllTickets(ticketsCaptor.getAllValues().getLast(), TicketStatus.SOLD);
    }

    @Test
    void shouldFinishASaleInterruptedAfterTheFirstStep() {
        Order confirming = RESERVED.startConfirmation(NOW);
        when(orderRepository.findById("O1")).thenReturn(Mono.just(confirming));
        when(ticketRepository.findByOrderId("O1")).thenReturn(
                Flux.fromIterable(RESERVED_TICKETS.stream().map(ticket -> ticket.startConfirmation("O1")).toList()));
        when(orderTicketsTransaction.commit(any(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("O1", "C1"))
                .assertNext(order -> assertEquals(OrderStatus.CONFIRMED, order.status()))
                .verifyComplete();

        verify(orderTicketsTransaction, times(1)).commit(any(), anyList());
    }

    @Test
    void shouldReturnAlreadyConfirmedOrderWithoutWriting() {
        Order confirmed = RESERVED.startConfirmation(NOW).confirm();
        when(orderRepository.findById("O1")).thenReturn(Mono.just(confirmed));

        StepVerifier.create(useCase.execute("O1", "C1"))
                .expectNext(confirmed)
                .verifyComplete();

        verifyNoInteractions(ticketRepository, orderTicketsTransaction);
    }

    @Test
    void shouldHideOrdersOfOtherCustomers() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));

        StepVerifier.create(useCase.execute("O1", "C2"))
                .expectError(OrderNotFoundException.class)
                .verify();

        verifyNoInteractions(orderTicketsTransaction);
    }

    @Test
    void shouldFailWhenOrderDoesNotExist() {
        when(orderRepository.findById("O1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("O1", "C1"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void shouldRejectConfirmationAfterTheReservationExpired() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));

        StepVerifier.create(useCaseAt(NOW.plus(TTL).plusSeconds(1)).execute("O1", "C1"))
                .expectError(ReservationExpiredException.class)
                .verify();

        verifyNoInteractions(orderTicketsTransaction);
    }

    @Test
    void shouldRejectConfirmationOfAnExpiredOrder() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED.expire()));

        StepVerifier.create(useCase.execute("O1", "C1"))
                .expectError(InvalidStateTransitionException.class)
                .verify();
    }

    @Test
    void shouldPropagateConflictWhenTheSweeperExpiredTheOrderFirst() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.fromIterable(RESERVED_TICKETS));
        when(orderTicketsTransaction.commit(any(), anyList()))
                .thenReturn(Mono.error(new ConcurrencyConflictException("Order", "O1")));

        StepVerifier.create(useCase.execute("O1", "C1"))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verify(orderTicketsTransaction, times(1)).commit(any(), anyList());
    }

    @Test
    void shouldReportAConflictWhenTicketsWereNotReadConsistently() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.just(RESERVED_TICKETS.getFirst()));

        StepVerifier.create(useCase.execute("O1", "C1"))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verifyNoInteractions(orderTicketsTransaction);
    }

    @Test
    void shouldReportAConflictWhenTheSweeperReleasedTheTicketsMeanwhile() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.fromIterable(
                RESERVED_TICKETS.stream().map(ticket -> ticket.release("O1")).toList()));

        StepVerifier.create(useCase.execute("O1", "C1"))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verifyNoInteractions(orderTicketsTransaction);
    }

    private ConfirmOrderUseCase useCaseAt(Instant now) {
        return new ConfirmOrderUseCase(orderRepository, ticketRepository, orderTicketsTransaction,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void assertAllTickets(List<Ticket> tickets, TicketStatus status) {
        assertEquals(2, tickets.size());
        assertTrue(tickets.stream().allMatch(ticket -> ticket.status() == status));
    }
}
