package co.com.nequi.ticketing.model.order;

public enum OrderStatus {
    PENDING, RESERVED, CONFIRMED, REJECTED, EXPIRED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == RESERVED || next == REJECTED;
            case RESERVED -> next == CONFIRMED || next == EXPIRED;
            case CONFIRMED, REJECTED, EXPIRED -> false;
        };
    }
}
