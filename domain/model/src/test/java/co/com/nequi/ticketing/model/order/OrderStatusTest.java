package co.com.nequi.ticketing.model.order;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, PENDING, false",
            "PENDING, RESERVED, true",
            "PENDING, CONFIRMED, false",
            "PENDING, REJECTED, true",
            "PENDING, EXPIRED, false",
            "RESERVED, PENDING, false",
            "RESERVED, RESERVED, false",
            "RESERVED, CONFIRMED, true",
            "RESERVED, REJECTED, false",
            "RESERVED, EXPIRED, true",
            "CONFIRMED, PENDING, false",
            "CONFIRMED, RESERVED, false",
            "CONFIRMED, CONFIRMED, false",
            "CONFIRMED, REJECTED, false",
            "CONFIRMED, EXPIRED, false",
            "REJECTED, PENDING, false",
            "REJECTED, RESERVED, false",
            "REJECTED, CONFIRMED, false",
            "REJECTED, REJECTED, false",
            "REJECTED, EXPIRED, false",
            "EXPIRED, PENDING, false",
            "EXPIRED, RESERVED, false",
            "EXPIRED, CONFIRMED, false",
            "EXPIRED, REJECTED, false",
            "EXPIRED, EXPIRED, false",
    })
    void shouldValidateTransitions(OrderStatus from, OrderStatus to, boolean expected) {
        assertEquals(expected, from.canTransitionTo(to));
    }
}
