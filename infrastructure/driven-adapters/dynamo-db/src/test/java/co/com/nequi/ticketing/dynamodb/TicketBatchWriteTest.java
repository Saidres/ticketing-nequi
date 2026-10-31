package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.ticket.Ticket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketBatchWriteTest {

    private static final DynamoDbTables TABLES = new DynamoDbTables("events", "tickets", "orders");
    private static final List<Ticket> INVENTORY = List.of(Ticket.available("E1-000001", "E1"));
    private static final BatchWriteItemResponse THROTTLED = BatchWriteItemResponse.builder()
            .unprocessedItems(Map.of("tickets", List.of(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(ItemMappers.toItem(INVENTORY.getFirst())).build())
                    .build())))
            .build();
    private static final BatchWriteItemResponse DONE = BatchWriteItemResponse.builder().build();

    @Mock
    private DynamoDbAsyncClient client;

    @Test
    @SuppressWarnings("unchecked")
    void shouldResendUnprocessedItemsUntilTheyAreWritten() {
        when(client.batchWriteItem(any(Consumer.class))).thenReturn(
                CompletableFuture.completedFuture(THROTTLED), CompletableFuture.completedFuture(DONE));

        StepVerifier.withVirtualTime(() -> new TicketDynamoRepository(client, TABLES).saveAll(INVENTORY))
                .thenAwait(Duration.ofSeconds(1))
                .verifyComplete();

        verify(client, times(2)).batchWriteItem(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGiveUpAfterTheLastAttempt() {
        when(client.batchWriteItem(any(Consumer.class))).thenReturn(CompletableFuture.completedFuture(THROTTLED));

        StepVerifier.withVirtualTime(() -> new TicketDynamoRepository(client, TABLES).saveAll(INVENTORY))
                .thenAwait(Duration.ofSeconds(5))
                .expectError(IllegalStateException.class)
                .verify();

        verify(client, times(5)).batchWriteItem(any(Consumer.class));
    }
}
