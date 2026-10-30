package co.com.nequi.ticketing.usecase.releaseexpired;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseExpiredReservationsUseCaseTest {

    private static final Instant RESERVED_AT = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Instant NOW = RESERVED_AT.plus(TTL).plusSeconds(1);

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

    private ReleaseExpiredReservationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReleaseExpiredReservationsUseCase(orderRepository, ticketRepository, orderTicketsTransaction,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldExpireOrderAndReturnItsTicketsToTheInventory() {
        when(orderRepository.findExpiredReservations(NOW)).thenReturn(Flux.just(reservedOrder("O1")));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.just(heldTicket("E1-000001", "O1")));
        when(orderTicketsTransaction.commit(any(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute()).expectNext(1L).verifyComplete();

        verify(orderTicketsTransaction).commit(orderCaptor.capture(), ticketsCaptor.capture());
        assertEquals(OrderStatus.EXPIRED, orderCaptor.getValue().status());
        assertEquals(1, ticketsCaptor.getValue().size());
        assertTrue(ticketsCaptor.getValue().stream()
                .allMatch(ticket -> ticket.status() == TicketStatus.AVAILABLE && ticket.orderId() == null));
    }

    @Test
    void shouldSkipOrdersConfirmedMeanwhileAndKeepReleasingTheRest() {
        when(orderRepository.findExpiredReservations(NOW))
                .thenReturn(Flux.just(reservedOrder("O1"), reservedOrder("O2")));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.just(heldTicket("E1-000001", "O1")));
        when(ticketRepository.findByOrderId("O2")).thenReturn(Flux.just(heldTicket("E1-000002", "O2")));
        when(orderTicketsTransaction.commit(any(), anyList()))
                .thenReturn(Mono.error(new ConcurrencyConflictException("Order", "O1")), Mono.empty());

        StepVerifier.create(useCase.execute()).expectNext(1L).verifyComplete();
    }

    @Test
    void shouldSkipOrderWhoseTicketsAreAlreadyBeingConfirmed() {
        when(orderRepository.findExpiredReservations(NOW))
                .thenReturn(Flux.just(reservedOrder("O1"), reservedOrder("O2")));
        when(ticketRepository.findByOrderId("O1"))
                .thenReturn(Flux.just(heldTicket("E1-000001", "O1").startConfirmation("O1")));
        when(ticketRepository.findByOrderId("O2")).thenReturn(Flux.just(heldTicket("E1-000002", "O2")));
        when(orderTicketsTransaction.commit(any(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute()).expectNext(1L).verifyComplete();

        verify(orderTicketsTransaction).commit(orderCaptor.capture(), anyList());
        assertEquals("O2", orderCaptor.getValue().id());
    }

    @Test
    void shouldSkipOrderWhoseTicketsWereNotReadConsistently() {
        when(orderRepository.findExpiredReservations(NOW)).thenReturn(Flux.just(reservedOrder("O1")));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute()).expectNext(0L).verifyComplete();

        verifyNoInteractions(orderTicketsTransaction);
    }

    @Test
    void shouldIgnoreOrdersThatAreNotExpiredYet() {
        Order stillValid = Order.pending("O1", "E1", "C1", 1, NOW).reserve(NOW, TTL);
        when(orderRepository.findExpiredReservations(NOW)).thenReturn(Flux.just(stillValid));

        StepVerifier.create(useCase.execute()).expectNext(0L).verifyComplete();

        verifyNoInteractions(ticketRepository, orderTicketsTransaction);
    }

    @Test
    void shouldPropagateUnexpectedErrorsSoTheNextRunRetries() {
        when(orderRepository.findExpiredReservations(NOW)).thenReturn(Flux.just(reservedOrder("O1")));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.just(heldTicket("E1-000001", "O1")));
        when(orderTicketsTransaction.commit(any(), anyList()))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.execute())
                .expectError(IllegalStateException.class)
                .verify();
    }

    private static Order reservedOrder(String orderId) {
        return Order.pending(orderId, "E1", "C1", 1, RESERVED_AT).reserve(RESERVED_AT, TTL);
    }

    private static Ticket heldTicket(String ticketId, String orderId) {
        return Ticket.available(ticketId, "E1").reserve(orderId);
    }
}
