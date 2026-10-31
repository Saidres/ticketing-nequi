package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class DynamoRepositoriesIntegrationTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Duration TTL = Duration.ofMinutes(10);

    private DynamoDbAsyncClient client;
    private DynamoDbTables tables;
    private EventDynamoRepository events;
    private TicketDynamoRepository tickets;
    private OrderDynamoRepository orders;
    private OrderTicketsDynamoTransaction transaction;

    @BeforeEach
    void setUp() {
        client = DynamoDbLocal.client();
        tables = DynamoDbLocal.createTables(client);
        events = new EventDynamoRepository(client, tables);
        tickets = new TicketDynamoRepository(client, tables);
        orders = new OrderDynamoRepository(client, tables);
        transaction = new OrderTicketsDynamoTransaction(client, tables);
    }

    @Test
    void shouldStoreAndReadEvents() {
        Event event = new Event("E1", "Rock Fest", NOW.plus(Duration.ofDays(30)), "Movistar Arena", 30);

        StepVerifier.create(events.save(event).then(events.findById("E1"))).expectNext(event).verifyComplete();
        StepVerifier.create(events.findAll()).expectNext(event).verifyComplete();
        StepVerifier.create(events.findById("missing")).verifyComplete();
    }

    @Test
    void shouldSaveInventoryInBatchesAndCountItByStatus() {
        StepVerifier.create(tickets.saveAll(inventory("E1", 30))
                        .then(tickets.countByEventIdAndStatus("E1", TicketStatus.AVAILABLE)))
                .expectNext(30L)
                .verifyComplete();
    }

    @Test
    void shouldFindTheRequestedNumberOfAvailableTicketsFromAnyStartingPoint() {
        tickets.saveAll(inventory("E1", 10)).block();

        StepVerifier.create(tickets.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 4).collectList())
                .assertNext(found -> {
                    assertEquals(4, found.size());
                    assertEquals(4, found.stream().map(Ticket::id).distinct().count());
                })
                .verifyComplete();
        StepVerifier.create(tickets.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 20).count())
                .expectNext(10L)
                .verifyComplete();
    }

    @Test
    void shouldReadForwardFromTheStartWithoutWrappingAround() {
        TicketDynamoRepository fromTheStart = new TicketDynamoRepository(client, tables, () -> 0L);
        tickets.saveAll(inventory("E1", 5)).block();

        StepVerifier.create(fromTheStart.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 5).count())
                .expectNext(5L)
                .verifyComplete();
    }

    @Test
    void shouldWrapAroundWhenTheRandomStartIsPastEveryTicket() {
        TicketDynamoRepository fromTheEnd = new TicketDynamoRepository(client, tables, () -> Long.MAX_VALUE);
        tickets.saveAll(inventory("E1", 5)).block();

        StepVerifier.create(fromTheEnd.findByEventIdAndStatus("E1", TicketStatus.AVAILABLE, 3).count())
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    void shouldCreateOrderOnlyOnce() {
        Order order = Order.pending("O1", "E1", "C1", 2, NOW);

        StepVerifier.create(orders.create(order).then(orders.findById("O1"))).expectNext(order).verifyComplete();
        StepVerifier.create(orders.create(order)).expectError(ConcurrencyConflictException.class).verify();
    }

    @Test
    void shouldRejectUpdatesBasedOnAStaleVersion() {
        Order pending = Order.pending("O1", "E1", "C1", 2, NOW);
        orders.create(pending).block();

        StepVerifier.create(orders.update(pending.reject())).expectNextCount(1).verifyComplete();
        StepVerifier.create(orders.update(pending.reserve(NOW, TTL)))
                .expectError(ConcurrencyConflictException.class)
                .verify();
    }

    @Test
    void shouldCommitOrderAndTicketsTogetherAndFindThemBack() {
        Order pending = Order.pending("O1", "E1", "C1", 2, NOW);
        List<Ticket> inventory = inventory("E1", 2);
        orders.create(pending).then(tickets.saveAll(inventory)).block();

        Order reserved = pending.reserve(NOW, TTL);
        StepVerifier.create(transaction.commit(reserved, held(inventory, "O1"))).verifyComplete();

        StepVerifier.create(orders.findById("O1")).expectNext(reserved).verifyComplete();
        StepVerifier.create(tickets.findByOrderId("O1").collectList())
                .assertNext(found -> assertTrue(found.stream().allMatch(ticket -> ticket.status() == TicketStatus.RESERVED)))
                .verifyComplete();
        StepVerifier.create(tickets.countByEventIdAndStatus("E1", TicketStatus.AVAILABLE)).expectNext(0L).verifyComplete();
    }

    @Test
    void shouldWriteNothingWhenAnyItemOfTheTransactionChangedMeanwhile() {
        List<Ticket> inventory = inventory("E1", 2);
        Order first = Order.pending("O1", "E1", "C1", 2, NOW);
        Order second = Order.pending("O2", "E1", "C2", 2, NOW);
        orders.create(first).then(orders.create(second)).then(tickets.saveAll(inventory)).block();
        transaction.commit(first.reserve(NOW, TTL), held(inventory, "O1")).block();

        StepVerifier.create(transaction.commit(second.reserve(NOW, TTL), held(inventory, "O2")))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        StepVerifier.create(orders.findById("O2").map(Order::status)).expectNext(OrderStatus.PENDING).verifyComplete();
    }

    @Test
    void shouldNeverHandTheSameTicketsToTwoConcurrentTransactions() {
        List<Ticket> inventory = inventory("E1", 2);
        List<Order> competitors = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> Order.pending("O" + i, "E1", "C" + i, 2, NOW))
                .toList();
        Flux.fromIterable(competitors).flatMap(orders::create).then(tickets.saveAll(inventory)).block();

        Long winners = Flux.fromIterable(competitors)
                .flatMap(order -> transaction.commit(order.reserve(NOW, TTL), held(inventory, order.id()))
                        .thenReturn(1)
                        .onErrorResume(ConcurrencyConflictException.class, conflict -> Mono.empty()))
                .count()
                .block();

        assertEquals(1L, winners);
    }

    @Test
    void shouldUpdateTicketsAtomicallyOnlyFromTheVersionThatWasRead() {
        List<Ticket> inventory = inventory("E1", 2);
        tickets.saveAll(inventory).block();
        List<Ticket> complimentary = inventory.stream().map(Ticket::grantComplimentary).toList();

        StepVerifier.create(tickets.updateAll(complimentary)).verifyComplete();
        StepVerifier.create(tickets.countByEventIdAndStatus("E1", TicketStatus.COMPLIMENTARY)).expectNext(2L).verifyComplete();
        StepVerifier.create(tickets.updateAll(inventory.stream().map(ticket -> ticket.reserve("O1")).toList()))
                .expectError(ConcurrencyConflictException.class)
                .verify();
    }

    @Test
    void shouldFindOnlyReservationsPastTheirDeadline() {
        Order expired = Order.pending("O1", "E1", "C1", 1, NOW).reserve(NOW.minus(TTL), TTL);
        Order valid = Order.pending("O2", "E1", "C2", 1, NOW).reserve(NOW, TTL);
        Order rejected = Order.pending("O3", "E1", "C3", 1, NOW).reject();
        Flux.just(expired, valid, rejected).flatMap(orders::create).blockLast();

        StepVerifier.create(orders.findExpiredReservations(NOW.plusSeconds(1)).map(Order::id))
                .expectNext("O1")
                .verifyComplete();
    }

    private static List<Ticket> inventory(String eventId, int size) {
        return IntStream.rangeClosed(1, size)
                .mapToObj(seat -> Ticket.available("%s-%06d".formatted(eventId, seat), eventId))
                .toList();
    }

    private static List<Ticket> held(List<Ticket> available, String orderId) {
        return available.stream().map(ticket -> ticket.reserve(orderId)).toList();
    }
}
