package co.com.nequi.ticketing.usecase.placeorder;

import static co.com.nequi.ticketing.model.shared.Preconditions.requireNonBlank;

public record PlaceOrderCommand(String customerId, String eventId, int quantity, String idempotencyKey) {

    public PlaceOrderCommand {
        requireNonBlank(customerId, "customerId");
        requireNonBlank(idempotencyKey, "idempotencyKey");
    }
}
