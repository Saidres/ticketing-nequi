package co.com.nequi.ticketing.model.shared;

public final class Preconditions {

    private Preconditions() {
    }

    public static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
