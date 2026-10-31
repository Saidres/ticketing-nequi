package co.com.nequi.ticketing.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

// One limiter per customer, so a single abusive client cannot starve the rest. The cache is bounded and idle
// limiters expire, so rotating X-Customer-Id cannot grow memory without limit
@Component
public class CustomerRateLimiters {

    static final int MAX_TRACKED_CUSTOMERS = 100_000;
    private static final Duration IDLE_EXPIRATION = Duration.ofMinutes(1);

    private final LoadingCache<String, RateLimiter> limiters;

    public CustomerRateLimiters(@Value("${ticketing.rate-limit.orders-per-second}") int ordersPerSecond) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(ordersPerSecond)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        this.limiters = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_CUSTOMERS)
                .expireAfterAccess(IDLE_EXPIRATION)
                .build(customerId -> RateLimiter.of("place-order:" + customerId, config));
    }

    public RateLimiter forCustomer(String customerId) {
        return limiters.get(customerId);
    }
}
