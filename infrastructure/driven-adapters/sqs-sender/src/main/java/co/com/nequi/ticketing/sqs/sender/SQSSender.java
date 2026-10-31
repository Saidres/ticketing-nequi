package co.com.nequi.ticketing.sqs.sender;

import co.com.nequi.ticketing.model.order.gateways.OrderQueue;
import co.com.nequi.ticketing.sqs.sender.config.SQSSenderProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Service
public class SQSSender implements OrderQueue {

    private static final Logger log = LogManager.getLogger(SQSSender.class);

    private final SqsAsyncClient client;
    private final SQSSenderProperties properties;
    private final CircuitBreaker circuitBreaker;

    public SQSSender(SqsAsyncClient client, SQSSenderProperties properties, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Mono<Void> publish(String orderId) {
        return Mono.fromFuture(() -> client.sendMessage(request -> request
                        .queueUrl(properties.queueUrl())
                        .messageBody(orderId)))
                .doOnNext(response -> log.debug("Order {} enqueued as message {}", orderId, response.messageId()))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .then();
    }
}
