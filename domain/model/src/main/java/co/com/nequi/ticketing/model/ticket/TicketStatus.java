package co.com.nequi.ticketing.model.ticket;

public enum TicketStatus {
    AVAILABLE, RESERVED, PENDING_CONFIRMATION, SOLD, COMPLIMENTARY;

    public boolean canTransitionTo(TicketStatus next) {
        return switch (this) {
            case AVAILABLE -> next == RESERVED || next == COMPLIMENTARY;
            case RESERVED -> next == AVAILABLE || next == PENDING_CONFIRMATION;
            case PENDING_CONFIRMATION -> next == AVAILABLE || next == SOLD;
            case SOLD, COMPLIMENTARY -> false;
        };
    }
}
