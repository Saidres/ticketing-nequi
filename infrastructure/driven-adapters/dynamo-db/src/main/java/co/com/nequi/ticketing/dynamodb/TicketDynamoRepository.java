package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import co.com.nequi.ticketing.model.ticket.gateways.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import static co.com.nequi.ticketing.dynamodb.ItemMappers.EVENT_STATUS;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.ORDER_ID;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.SHUFFLE;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.eventStatus;

@Repository
public class TicketDynamoRepository implements TicketRepository {

    private static final int BATCH_SIZE = 25;
    private static final int PARALLEL_BATCHES = 8;
    private static final int MAX_BATCH_ATTEMPTS = 5;
    private static final Duration FIRST_BATCH_BACKOFF = Duration.ofMillis(50);

    private final DynamoDbAsyncClient client;
    private final DynamoDbTables tables;
    private final LongSupplier randomStart;

    @Autowired
    public TicketDynamoRepository(DynamoDbAsyncClient client, DynamoDbTables tables) {
        this(client, tables, () -> ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
    }

    TicketDynamoRepository(DynamoDbAsyncClient client, DynamoDbTables tables, LongSupplier randomStart) {
        this.client = client;
        this.tables = tables;
        this.randomStart = randomStart;
    }

    @Override
    public Mono<Void> saveAll(List<Ticket> tickets) {
        return Flux.fromIterable(tickets)
                .map(ticket -> WriteRequest.builder()
                        .putRequest(PutRequest.builder().item(ItemMappers.toItem(ticket)).build())
                        .build())
                .buffer(BATCH_SIZE)
                .flatMap(batch -> writeBatch(Map.of(tables.tickets(), batch), 1), PARALLEL_BATCHES)
                .then();
    }

    // Throttled batches come back as unprocessed items; they are resent with exponential backoff
    private Mono<Void> writeBatch(Map<String, List<WriteRequest>> requests, int attempt) {
        return Mono.fromFuture(() -> client.batchWriteItem(request -> request.requestItems(requests)))
                .flatMap(response -> {
                    if (!response.hasUnprocessedItems() || response.unprocessedItems().isEmpty()) {
                        return Mono.empty();
                    }
                    if (attempt == MAX_BATCH_ATTEMPTS) {
                        return Mono.error(new IllegalStateException("Tickets could not be written after retries"));
                    }
                    return Mono.delay(FIRST_BATCH_BACKOFF.multipliedBy(1L << (attempt - 1)))
                            .then(writeBatch(response.unprocessedItems(), attempt + 1));
                });
    }

    // Starts at a random point of the index and wraps around, so concurrent consumers pick different tickets
    @Override
    public Flux<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status, int limit) {
        return Mono.fromSupplier(randomStart::getAsLong)
                .flatMapMany(start -> queryFrom(eventId, status, ">=", start, limit)
                        .collectList()
                        .flatMapMany(first -> first.size() >= limit
                                ? Flux.fromIterable(first)
                                : Flux.fromIterable(first)
                                .concatWith(queryFrom(eventId, status, "<", start, limit - first.size()))));
    }

    private Flux<Ticket> queryFrom(String eventId, TicketStatus status, String operator, long start, int limit) {
        QueryRequest request = byEventStatus()
                .keyConditionExpression("#eventStatus = :eventStatus AND #shuffle " + operator + " :start")
                .expressionAttributeNames(Map.of("#eventStatus", EVENT_STATUS, "#shuffle", SHUFFLE))
                .expressionAttributeValues(Map.of(
                        ":eventStatus", Items.text(eventStatus(eventId, status)),
                        ":start", Items.number(start)))
                .limit(limit)
                .build();
        return Mono.fromFuture(() -> client.query(request))
                .flatMapIterable(QueryResponse::items)
                .map(ItemMappers::toTicket);
    }

    @Override
    public Flux<Ticket> findByOrderId(String orderId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tables.tickets())
                .indexName(DynamoDbTables.TICKETS_BY_ORDER)
                .keyConditionExpression("#orderId = :orderId")
                .expressionAttributeNames(Map.of("#orderId", ORDER_ID))
                .expressionAttributeValues(Map.of(":orderId", Items.text(orderId)))
                .build();
        return Flux.from(client.queryPaginator(request))
                .flatMapIterable(QueryResponse::items)
                .map(ItemMappers::toTicket);
    }

    @Override
    public Mono<Long> countByEventIdAndStatus(String eventId, TicketStatus status) {
        QueryRequest request = byEventStatus()
                .keyConditionExpression("#eventStatus = :eventStatus")
                .expressionAttributeNames(Map.of("#eventStatus", EVENT_STATUS))
                .expressionAttributeValues(Map.of(":eventStatus", Items.text(eventStatus(eventId, status))))
                .select(Select.COUNT)
                .build();
        return Flux.from(client.queryPaginator(request))
                .map(response -> (long) response.count())
                .reduce(0L, Long::sum);
    }

    private QueryRequest.Builder byEventStatus() {
        return QueryRequest.builder()
                .tableName(tables.tickets())
                .indexName(DynamoDbTables.TICKETS_BY_EVENT_STATUS);
    }
}
