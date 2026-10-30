package co.com.nequi.ticketing.sqs.sender;

import co.com.nequi.ticketing.model.order.gateways.OrderQueue;
import co.com.nequi.ticketing.sqs.sender.config.SQSSenderProperties;
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

    public SQSSender(SqsAsyncClient client, SQSSenderProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Mono<Void> publish(String orderId) {
        return Mono.fromFuture(() -> client.sendMessage(request -> request
                        .queueUrl(properties.queueUrl())
                        .messageBody(orderId)))
                .doOnNext(response -> log.debug("Order {} enqueued as message {}", orderId, response.messageId()))
                .then();
    }
}
