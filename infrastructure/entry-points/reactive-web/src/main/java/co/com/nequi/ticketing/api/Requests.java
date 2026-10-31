package co.com.nequi.ticketing.api;

import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import static co.com.nequi.ticketing.model.shared.Preconditions.requireNonBlank;

final class Requests {

    static final String CUSTOMER_ID = "X-Customer-Id";
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private Requests() {
    }

    static Mono<String> requiredHeader(ServerRequest request, String name) {
        return Mono.fromCallable(() -> requireNonBlank(request.headers().firstHeader(name), name + " header"));
    }

    static Mono<String> customerId(ServerRequest request) {
        return requiredHeader(request, CUSTOMER_ID);
    }

    static <T> Mono<T> requiredBody(ServerRequest request, Class<T> type) {
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("request body is required")));
    }
}
