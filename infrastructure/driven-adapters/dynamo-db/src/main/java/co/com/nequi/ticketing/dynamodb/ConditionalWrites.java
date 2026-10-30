package co.com.nequi.ticketing.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Map;
import java.util.Set;

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

    static boolean isConflict(TransactionCanceledException error) {
        return error.cancellationReasons().stream()
                .map(CancellationReason::code)
                .anyMatch(CONFLICT_REASONS::contains);
    }
}
