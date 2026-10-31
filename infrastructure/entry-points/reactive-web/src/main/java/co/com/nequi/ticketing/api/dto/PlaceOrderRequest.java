package co.com.nequi.ticketing.api.dto;

import co.com.nequi.ticketing.usecase.placeorder.PlaceOrderCommand;

public record PlaceOrderRequest(String eventId, int quantity) {

    public PlaceOrderCommand toCommand(String customerId, String idempotencyKey) {
        return new PlaceOrderCommand(customerId, eventId, quantity, idempotencyKey);
    }
}
