package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static co.com.nequi.ticketing.dynamodb.ItemMappers.VERSION;

final class ConditionalWrites {

    private static final Set<String> CONFLICT_REASONS = Set.of("ConditionalCheckFailed", "TransactionConflict");

    static final String UNCHANGED = "#version = :expected";
    static final Map<String, String> UNCHANGED_NAMES = Map.of("#version", VERSION);

    private ConditionalWrites() {
    }

    // Optimistic locking: the write only succeeds if nobody changed the item since it was read
    static Map<String, AttributeValue> unchangedValues(long newVersion) {
        return Map.of(":expected", Items.number(newVersion - 1));
    }

    static Put putIfUnchanged(String table, Map<String, AttributeValue> item, long newVersion) {
        return Put.builder()
                .tableName(table)
                .item(item)
                .conditionExpression(UNCHANGED)
                .expressionAttributeNames(UNCHANGED_NAMES)
                .expressionAttributeValues(unchangedValues(newVersion))
                .build();
    }

    // One all-or-nothing TransactWriteItems; a lost condition becomes a retryable ConcurrencyConflictException
    static Mono<Void> writeAtomically(DynamoDbAsyncClient client, List<Put> puts,
                                      Supplier<ConcurrencyConflictException> onConflict) {
        List<TransactWriteItem> writes = puts.stream().map(put -> TransactWriteItem.builder().put(put).build()).toList();
        return Mono.fromFuture(() -> client.transactWriteItems(request -> request.transactItems(writes)))
                .onErrorMap(error -> error instanceof TransactionCanceledException canceled && isConflict(canceled),
                        conflict -> onConflict.get())
                .then();
    }

    private static boolean isConflict(TransactionCanceledException error) {
        return error.cancellationReasons().stream()
                .map(CancellationReason::code)
                .anyMatch(CONFLICT_REASONS::contains);
    }
}
