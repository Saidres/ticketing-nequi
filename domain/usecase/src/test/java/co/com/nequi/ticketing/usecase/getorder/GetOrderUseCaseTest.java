package co.com.nequi.ticketing.usecase.getorder;

import co.com.nequi.ticketing.model.exception.OrderNotFoundException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Order RESERVED = Order.pending("O1", "E1", "C1", 1, NOW).reserve(NOW, Duration.ofMinutes(10));
    private static final Ticket HELD = Ticket.available("E1-000001", "E1").reserve("O1");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private GetOrderUseCase useCase;

    @Test
    void shouldReturnOrderWithTheStatusOfEachTicket() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.just(HELD));

        StepVerifier.create(useCase.execute("O1", "C1"))
                .assertNext(details -> {
                    assertEquals(RESERVED, details.order());
                    assertEquals(List.of(HELD), details.tickets());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnOrderWithoutTicketsWhileItIsPending() {
        Order pending = Order.pending("O1", "E1", "C1", 1, NOW);
        when(orderRepository.findById("O1")).thenReturn(Mono.just(pending));
        when(ticketRepository.findByOrderId("O1")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("O1", "C1"))
                .assertNext(details -> assertEquals(List.of(), details.tickets()))
                .verifyComplete();
    }

    @Test
    void shouldHideOrdersOfOtherCustomers() {
        when(orderRepository.findById("O1")).thenReturn(Mono.just(RESERVED));

        StepVerifier.create(useCase.execute("O1", "C2"))
                .expectError(OrderNotFoundException.class)
                .verify();

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void shouldFailWhenOrderDoesNotExist() {
        when(orderRepository.findById("O1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("O1", "C1"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }
}
