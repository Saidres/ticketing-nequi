package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.order.gateways.OrderRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.Map;

import static co.com.nequi.ticketing.dynamodb.ItemMappers.ORDER_ID;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.RESERVED_UNTIL;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.STATUS;

@Repository
public class OrderDynamoRepository implements OrderRepository {

    private final DynamoDbAsyncClient client;
    private final DynamoDbTables tables;

    public OrderDynamoRepository(DynamoDbAsyncClient client, DynamoDbTables tables) {
        this.client = client;
        this.tables = tables;
    }

    @Override
    public Mono<Order> create(Order order) {
        return Mono.fromFuture(() -> client.putItem(request -> request
                        .tableName(tables.orders())
                        .item(ItemMappers.toItem(order))
                        .conditionExpression("attribute_not_exists(#orderId)")
                        .expressionAttributeNames(Map.of("#orderId", ORDER_ID))))
                .thenReturn(order)
                .onErrorMap(ConditionalCheckFailedException.class, conflict -> conflictOn(order));
    }

    @Override
    public Mono<Order> update(Order order) {
        return Mono.fromFuture(() -> client.putItem(request -> request
                        .tableName(tables.orders())
                        .item(ItemMappers.toItem(order))
                        .conditionExpression(ConditionalWrites.UNCHANGED)
                        .expressionAttributeNames(ConditionalWrites.UNCHANGED_NAMES)
                        .expressionAttributeValues(ConditionalWrites.unchangedValues(order.version()))))
                .thenReturn(order)
                .onErrorMap(ConditionalCheckFailedException.class, conflict -> conflictOn(order));
    }

    @Override
    public Mono<Order> findById(String id) {
        return Mono.fromFuture(() -> client.getItem(request -> request
                        .tableName(tables.orders())
                        .key(Map.of(ORDER_ID, Items.text(id)))
                        .consistentRead(true)))
                .filter(GetItemResponse::hasItem)
                .map(response -> ItemMappers.toOrder(response.item()));
    }

    // Sparse index: only RESERVED orders carry reservedUntil, so only they are indexed
    @Override
    public Flux<Order> findExpiredReservations(Instant now) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tables.orders())
                .indexName(DynamoDbTables.ORDERS_BY_STATUS_AND_DEADLINE)
                .keyConditionExpression("#status = :reserved AND #reservedUntil < :now")
                .expressionAttributeNames(Map.of("#status", STATUS, "#reservedUntil", RESERVED_UNTIL))
                .expressionAttributeValues(Map.of(
                        ":reserved", Items.text(OrderStatus.RESERVED.name()),
                        ":now", Items.instant(now)))
                .build();
        return Flux.from(client.queryPaginator(request))
                .flatMapIterable(QueryResponse::items)
                .map(ItemMappers::toOrder);
    }

    private static ConcurrencyConflictException conflictOn(Order order) {
        return new ConcurrencyConflictException("Order", order.id());
    }
}
