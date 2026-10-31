package co.com.nequi.ticketing.config;

import co.com.nequi.ticketing.usecase.processorder.ReservationSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ReservationSettings reservationSettings(@Value("${ticketing.reservation-ttl}") Duration ttl) {
        return new ReservationSettings(ttl);
    }
}
