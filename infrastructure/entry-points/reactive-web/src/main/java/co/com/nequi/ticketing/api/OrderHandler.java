package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.dto.OrderResponse;
import co.com.nequi.ticketing.api.dto.PlaceOrderRequest;
import co.com.nequi.ticketing.usecase.confirmorder.ConfirmOrderUseCase;
import co.com.nequi.ticketing.usecase.getorder.GetOrderUseCase;
import co.com.nequi.ticketing.usecase.placeorder.PlaceOrderUseCase;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static co.com.nequi.ticketing.api.Requests.IDEMPOTENCY_KEY;

@Component
public class OrderHandler {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final RateLimiterRegistry rateLimiters;

    public OrderHandler(PlaceOrderUseCase placeOrderUseCase, GetOrderUseCase getOrderUseCase,
                        ConfirmOrderUseCase confirmOrderUseCase, RateLimiterRegistry rateLimiters) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.rateLimiters = rateLimiters;
    }

    // 202 Accepted: the purchase is only queued here; GET /orders/{id} tells how it ended.
    // Headers are validated before the body is read, so invalid requests cost nothing
    public Mono<ServerResponse> placeOrder(ServerRequest request) {
        return Mono.zip(Requests.customerId(request), Requests.requiredHeader(request, IDEMPOTENCY_KEY))
                .flatMap(headers -> Requests.requiredBody(request, PlaceOrderRequest.class)
                        .map(body -> body.toCommand(headers.getT1(), headers.getT2())))
                .flatMap(command -> placeOrderUseCase.execute(command)
                        .transformDeferred(RateLimiterOperator.of(rateLimiterFor(command.customerId()))))
                .flatMap(order -> ServerResponse.accepted()
                        .location(URI.create("/api/v1/orders/" + order.id()))
                        .bodyValue(OrderResponse.of(order, List.of())))
                .onErrorResume(ApiErrors::toResponse);
    }

    // One limiter per customer, so a single abusive client cannot starve the rest
    private RateLimiter rateLimiterFor(String customerId) {
        return rateLimiters.rateLimiter("place-order:" + customerId);
    }

    public Mono<ServerResponse> getOrder(ServerRequest request) {
        return Requests.customerId(request)
                .flatMap(customerId -> getOrderUseCase.execute(request.pathVariable("orderId"), customerId))
                .flatMap(details -> ServerResponse.ok().bodyValue(OrderResponse.of(details.order(), details.tickets())))
                .onErrorResume(ApiErrors::toResponse);
    }

    public Mono<ServerResponse> confirmOrder(ServerRequest request) {
        return Requests.customerId(request)
                .flatMap(customerId -> confirmOrderUseCase.execute(request.pathVariable("orderId"), customerId))
                .flatMap(order -> ServerResponse.ok().bodyValue(OrderResponse.of(order, List.of())))
                .onErrorResume(ApiErrors::toResponse);
    }
}
