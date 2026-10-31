package co.com.nequi.ticketing.api.dto;

import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.order.OrderStatus;
import co.com.nequi.ticketing.model.ticket.Ticket;

import java.time.Instant;
import java.util.List;

// Exposes each ticket's status with the names of the challenge (AVAILABLE, RESERVED, PENDING_CONFIRMATION, SOLD...)
public record OrderResponse(String orderId, String eventId, int quantity, OrderStatus status, Instant createdAt,
                            Instant reservedUntil, List<TicketView> tickets) {

    public static OrderResponse of(Order order, List<Ticket> tickets) {
        return new OrderResponse(order.id(), order.eventId(), order.quantity(), order.status(), order.createdAt(),
                order.reservedUntil(), tickets.stream().map(TicketView::of).toList());
    }
}
