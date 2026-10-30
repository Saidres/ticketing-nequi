package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.event.Event;
import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.ticket.Ticket;
import co.com.nequi.ticketing.model.ticket.TicketStatus;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static co.com.nequi.ticketing.dynamodb.Items.instant;
import static co.com.nequi.ticketing.dynamodb.Items.number;
import static co.com.nequi.ticketing.dynamodb.Items.putIfPresent;
import static co.com.nequi.ticketing.dynamodb.Items.text;

final class ItemMappers {

    static final String EVENT_ID = "eventId";
    static final String TICKET_ID = "ticketId";
    static final String ORDER_ID = "orderId";
    static final String STATUS = "status";
    static final String VERSION = "version";
    static final String EVENT_STATUS = "eventStatus";
    static final String SHUFFLE = "shuffle";
    static final String RESERVED_UNTIL = "reservedUntil";
    static final String NAME = "name";
    static final String DATE = "date";
    static final String VENUE = "venue";
    static final String TOTAL_CAPACITY = "totalCapacity";
    static final String CUSTOMER_ID = "customerId";
    static final String QUANTITY = "quantity";
    static final String CREATED_AT = "createdAt";

    private ItemMappers() {
    }

    static Map<String, AttributeValue> toItem(Event event) {
        return Map.of(
                EVENT_ID, text(event.id()),
                NAME, text(event.name()),
                DATE, instant(event.date()),
                VENUE, text(event.venue()),
                TOTAL_CAPACITY, number(event.totalCapacity()));
    }

    static Event toEvent(Map<String, AttributeValue> item) {
        return new Event(text(item, EVENT_ID), text(item, NAME), instant(item, DATE), text(item, VENUE),
                (int) number(item, TOTAL_CAPACITY));
    }

    // eventStatus and shuffle are derived on every write, so a full Put never drops them from the index
    static Map<String, AttributeValue> toItem(Ticket ticket) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(TICKET_ID, text(ticket.id()));
        item.put(EVENT_ID, text(ticket.eventId()));
        item.put(STATUS, text(ticket.status().name()));
        item.put(VERSION, number(ticket.version()));
        item.put(EVENT_STATUS, text(eventStatus(ticket.eventId(), ticket.status())));
        item.put(SHUFFLE, number(shuffleOf(ticket.id())));
        putIfPresent(item, ORDER_ID, ticket.orderId());
        return item;
    }

    static Ticket toTicket(Map<String, AttributeValue> item) {
        return new Ticket(text(item, TICKET_ID), text(item, EVENT_ID), TicketStatus.valueOf(text(item, STATUS)),
                text(item, ORDER_ID), number(item, VERSION));
    }

    static Map<String, AttributeValue> toItem(Order order) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(ORDER_ID, text(order.id()));
        item.put(EVENT_ID, text(order.eventId()));
        item.put(CUSTOMER_ID, text(order.customerId()));
        item.put(QUANTITY, number(order.quantity()));
        item.put(STATUS, text(order.status().name()));
        item.put(CREATED_AT, instant(order.createdAt()));
        item.put(VERSION, number(order.version()));
        putIfPresent(item, RESERVED_UNTIL, order.reservedUntil());
        return item;
    }

    static Order toOrder(Map<String, AttributeValue> item) {
        return new Order(text(item, ORDER_ID), text(item, EVENT_ID), text(item, CUSTOMER_ID),
                (int) number(item, QUANTITY), OrderStatus.valueOf(text(item, STATUS)), instant(item, CREATED_AT),
                instant(item, RESERVED_UNTIL), number(item, VERSION));
    }

    static String eventStatus(String eventId, TicketStatus status) {
        return eventId + "#" + status.name();
    }

    // Stable pseudo-random position of the ticket inside its event/status index partition
    static long shuffleOf(String ticketId) {
        return UUID.nameUUIDFromBytes(ticketId.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits()
                & Long.MAX_VALUE;
    }
}
