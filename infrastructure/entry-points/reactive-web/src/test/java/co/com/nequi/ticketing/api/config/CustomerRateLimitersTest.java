package co.com.nequi.ticketing.api.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerRateLimitersTest {

    private final CustomerRateLimiters limiters = new CustomerRateLimiters(1);

    @Test
    void shouldGiveEachCustomerItsOwnLimiter() {
        RateLimiter ana = limiters.forCustomer("ana");

        assertSame(ana, limiters.forCustomer("ana"));
        assertNotSame(ana, limiters.forCustomer("luis"));
    }

    @Test
    void shouldRejectOnlyTheCustomerThatExceededItsLimit() {
        assertTrue(limiters.forCustomer("ana").acquirePermission());
        assertFalse(limiters.forCustomer("ana").acquirePermission());
        assertTrue(limiters.forCustomer("luis").acquirePermission());
    }
}
