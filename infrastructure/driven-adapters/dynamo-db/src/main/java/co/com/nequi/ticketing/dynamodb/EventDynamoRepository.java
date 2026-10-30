package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.event.gateways.EventRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.Map;

import static co.com.nequi.ticketing.dynamodb.ItemMappers.EVENT_ID;

@Repository
public class EventDynamoRepository implements EventRepository {

    private final DynamoDbAsyncClient client;
    private final DynamoDbTables tables;

    public EventDynamoRepository(DynamoDbAsyncClient client, DynamoDbTables tables) {
        this.client = client;
        this.tables = tables;
    }

    @Override
    public Mono<Event> save(Event event) {
        return Mono.fromFuture(() -> client.putItem(request -> request
                        .tableName(tables.events())
                        .item(ItemMappers.toItem(event))))
                .thenReturn(event);
    }

    @Override
    public Mono<Event> findById(String id) {
        return Mono.fromFuture(() -> client.getItem(request -> request
                        .tableName(tables.events())
                        .key(Map.of(EVENT_ID, Items.text(id)))
                        .consistentRead(true)))
                .filter(GetItemResponse::hasItem)
                .map(response -> ItemMappers.toEvent(response.item()));
    }

    // Events are few compared with tickets, so a paginated scan is acceptable here
    @Override
    public Flux<Event> findAll() {
        return Flux.from(client.scanPaginator(request -> request.tableName(tables.events())))
                .flatMapIterable(ScanResponse::items)
                .map(ItemMappers::toEvent);
    }
}
