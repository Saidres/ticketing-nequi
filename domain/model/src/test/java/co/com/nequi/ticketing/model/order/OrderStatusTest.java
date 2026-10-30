package co.com.nequi.ticketing.model.order;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, PENDING, false",
            "PENDING, RESERVED, true",
            "PENDING, CONFIRMING, false",
            "PENDING, CONFIRMED, false",
            "PENDING, REJECTED, true",
            "PENDING, EXPIRED, false",

            "RESERVED, PENDING, false",
            "RESERVED, RESERVED, false",
            "RESERVED, CONFIRMING, true",
            "RESERVED, CONFIRMED, false",
            "RESERVED, REJECTED, false",
            "RESERVED, EXPIRED, true",

            "CONFIRMING, PENDING, false",
            "CONFIRMING, RESERVED, false",
            "CONFIRMING, CONFIRMING, false",
            "CONFIRMING, CONFIRMED, true",
            "CONFIRMING, REJECTED, false",
            "CONFIRMING, EXPIRED, false",

            "CONFIRMED, PENDING, false",
            "CONFIRMED, RESERVED, false",
            "CONFIRMED, CONFIRMING, false",
            "CONFIRMED, CONFIRMED, false",
            "CONFIRMED, REJECTED, false",
            "CONFIRMED, EXPIRED, false",

            "REJECTED, PENDING, false",
            "REJECTED, RESERVED, false",
            "REJECTED, CONFIRMING, false",
            "REJECTED, CONFIRMED, false",
            "REJECTED, REJECTED, false",
            "REJECTED, EXPIRED, false",

            "EXPIRED, PENDING, false",
            "EXPIRED, RESERVED, false",
            "EXPIRED, CONFIRMING, false",
            "EXPIRED, CONFIRMED, false",
            "EXPIRED, REJECTED, false",
            "EXPIRED, EXPIRED, false",
    })
    void shouldValidateTransitions(OrderStatus from, OrderStatus to, boolean expected) {
        assertEquals(expected, from.canTransitionTo(to));
    }
}
