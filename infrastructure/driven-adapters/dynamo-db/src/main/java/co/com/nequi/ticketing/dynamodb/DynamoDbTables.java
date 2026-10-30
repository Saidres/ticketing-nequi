package co.com.nequi.ticketing.dynamodb;

// Table names come from configuration so each environment gets its own isolated tables
public record DynamoDbTables(String events, String tickets, String orders) {

    public static final String TICKETS_BY_EVENT_STATUS = "eventStatus-shuffle-index";
    public static final String TICKETS_BY_ORDER = "orderId-index";
    public static final String ORDERS_BY_STATUS_AND_DEADLINE = "status-reservedUntil-index";
}
