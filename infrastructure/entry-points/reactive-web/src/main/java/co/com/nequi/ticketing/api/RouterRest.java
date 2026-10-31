package co.com.nequi.ticketing.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(EventHandler events, OrderHandler orders) {
        return route(POST("/api/v1/events"), events::createEvent)
                .andRoute(GET("/api/v1/events"), events::listEvents)
                .andRoute(GET("/api/v1/events/{eventId}"), events::getEvent)
                .andRoute(GET("/api/v1/events/{eventId}/availability"), events::getAvailability)
                .andRoute(GET("/api/v1/events/{eventId}/availability/stream"), events::streamAvailability)
                .andRoute(POST("/api/v1/orders"), orders::placeOrder)
                .andRoute(GET("/api/v1/orders/{orderId}"), orders::getOrder)
                .andRoute(POST("/api/v1/orders/{orderId}/confirm"), orders::confirmOrder);
    }
}
