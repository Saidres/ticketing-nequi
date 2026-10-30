package co.com.nequi.ticketing.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;

final class Items {

    private Items() {
    }

    static AttributeValue text(String value) {
        return AttributeValue.fromS(value);
    }

    static AttributeValue number(long value) {
        return AttributeValue.fromN(Long.toString(value));
    }

    // Instants are stored as epoch millis so range conditions compare numbers, not strings
    static AttributeValue instant(Instant value) {
        return number(value.toEpochMilli());
    }

    static void putIfPresent(Map<String, AttributeValue> item, String name, String value) {
        if (value != null) {
            item.put(name, text(value));
        }
    }

    static void putIfPresent(Map<String, AttributeValue> item, String name, Instant value) {
        if (value != null) {
            item.put(name, instant(value));
        }
    }

    static String text(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }

    static long number(Map<String, AttributeValue> item, String name) {
        return Long.parseLong(item.get(name).n());
    }

    static Instant instant(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : Instant.ofEpochMilli(Long.parseLong(value.n()));
    }
}
