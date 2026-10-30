package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.gateways.OrderTicketsTransaction;
import co.com.nequi.ticketing.model.ticket.Ticket;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.List;
import java.util.stream.Stream;

@Repository
public class OrderTicketsDynamoTransaction implements OrderTicketsTransaction {

    private final DynamoDbAsyncClient client;
    private final DynamoDbTables tables;

    public OrderTicketsDynamoTransaction(DynamoDbAsyncClient client, DynamoDbTables tables) {
        this.client = client;
        this.tables = tables;
    }

    // At most 1 order + 6 tickets, well under the 100-item TransactWriteItems limit
    @Override
    public Mono<Void> commit(Order order, List<Ticket> tickets) {
        List<TransactWriteItem> writes = Stream.concat(
                        Stream.of(ConditionalWrites.putIfUnchanged(tables.orders(), ItemMappers.toItem(order),
                                order.version())),
                        tickets.stream().map(ticket -> ConditionalWrites.putIfUnchanged(tables.tickets(),
                                ItemMappers.toItem(ticket), ticket.version())))
                .map(put -> TransactWriteItem.builder().put(put).build())
                .toList();
        return Mono.fromFuture(() -> client.transactWriteItems(request -> request.transactItems(writes)))
                .onErrorMap(error -> error instanceof TransactionCanceledException canceled
                                && ConditionalWrites.isConflict(canceled),
                        conflict -> new ConcurrencyConflictException("Order", order.id()))
                .then();
    }
}
