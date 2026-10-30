package co.com.nequi.ticketing.model.order;

public enum OrderStatus {
    PENDING, RESERVED, CONFIRMING, CONFIRMED, REJECTED, EXPIRED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == RESERVED || next == REJECTED;
            case RESERVED -> next == CONFIRMING || next == EXPIRED;
            case CONFIRMING -> next == CONFIRMED;
            case CONFIRMED, REJECTED, EXPIRED -> false;
        };
    }
}
