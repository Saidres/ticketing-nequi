package co.com.nequi.ticketing.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

class ConfigTest {

    private final WebTestClient client = WebTestClient
            .bindToRouterFunction(RouterFunctions.route(GET("/ping"), request -> ServerResponse.ok().build()))
            .webFilter(new SecurityHeadersConfig(), new CorsConfig().corsWebFilter(List.of("http://localhost:4200")))
            .configureClient()
            .baseUrl("http://api.ticketing.test")
            .build();

    @Test
    void shouldAddSecurityHeadersToEveryResponse() {
        client.get().uri("/ping").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().doesNotExist("Server");
    }

    @Test
    void shouldAllowOnlyConfiguredOrigins() {
        client.get().uri("/ping").header("Origin", "http://localhost:4200").exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");
        client.get().uri("/ping").header("Origin", "http://evil.example").exchange()
                .expectStatus().isForbidden();
    }
}
