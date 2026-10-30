package co.com.nequi.ticketing.usecase.getavailability;

// reserved counts RESERVED and PENDING_CONFIRMATION tickets: held for a customer but not yet sold
public record Availability(String eventId, int totalCapacity, long available, long reserved, long sold,
                           long complimentary) {
}
