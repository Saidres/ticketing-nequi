package co.com.nequi.ticketing.usecase.processorder;

import co.com.nequi.ticketing.model.order.Order;

import java.time.Duration;

public record ReservationSettings(Duration ttl) {

    // Fails at startup instead of on every message when the configured ttl breaks the domain limit
    public ReservationSettings {
        Order.requireValidReservationTtl(ttl);
    }
}
