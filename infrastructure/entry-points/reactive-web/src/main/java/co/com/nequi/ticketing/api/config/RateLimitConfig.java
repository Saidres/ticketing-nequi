package co.com.nequi.ticketing.api.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    // Rejects immediately (no waiting) once a customer exceeds its purchases per second
    @Bean
    public RateLimiterRegistry rateLimiterRegistry(@Value("${ticketing.rate-limit.orders-per-second}") int ordersPerSecond) {
        return RateLimiterRegistry.of(RateLimiterConfig.custom()
                .limitForPeriod(ordersPerSecond)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
    }
}
