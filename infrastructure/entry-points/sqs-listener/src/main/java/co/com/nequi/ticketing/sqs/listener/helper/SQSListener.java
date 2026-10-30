package co.com.nequi.ticketing.sqs.listener.helper;

import co.com.nequi.ticketing.sqs.listener.config.SQSProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.function.Function;

// SmartLifecycle: polling starts only once the whole context is ready and stops on shutdown
public class SQSListener implements SmartLifecycle {

    private static final Logger log = LogManager.getLogger(SQSListener.class);
    private static final Duration PAUSE_AFTER_RECEIVE_FAILURE = Duration.ofSeconds(1);

    private final SqsAsyncClient client;
    private final SQSProperties properties;
    private final Function<Message, Mono<Void>> processor;
    private Disposable.Composite pollers = Disposables.composite();

    public SQSListener(SqsAsyncClient client, SQSProperties properties, Function<Message, Mono<Void>> processor) {
        this.client = client;
        this.properties = properties;
        this.processor = processor;
    }

    // subscribeOn keeps start() from looping on the caller thread when a receive completes immediately
    @Override
    public synchronized void start() {
        pollers = Disposables.composite();
        for (var i = 0; i < properties.concurrentPollers(); i++) {
            pollers.add(poll().repeat().subscribeOn(Schedulers.boundedElastic()).subscribe());
        }
    }

    @Override
    public synchronized void stop() {
        pollers.dispose();
    }

    @Override
    public synchronized boolean isRunning() {
        return pollers.size() > 0 && !pollers.isDisposed();
    }

    // One long-polling round that never errors, so repeat() keeps the poller alive
    Flux<Void> poll() {
        return receive()
                .flatMapIterable(ReceiveMessageResponse::messages)
                .flatMap(this::handle, properties.maxNumberOfMessages());
    }

    private Mono<ReceiveMessageResponse> receive() {
        return Mono.fromFuture(() -> client.receiveMessage(request -> request
                        .queueUrl(properties.queueUrl())
                        .maxNumberOfMessages(properties.maxNumberOfMessages())
                        .waitTimeSeconds(properties.waitTimeSeconds())
                        .visibilityTimeout(properties.visibilityTimeoutSeconds())))
                .onErrorResume(error -> {
                    log.error("Could not receive messages from {}", properties.queueUrl(), error);
                    return Mono.delay(PAUSE_AFTER_RECEIVE_FAILURE).then(Mono.empty());
                });
    }

    // Deleted only after success; a failed message reappears after the visibility timeout and ends in the DLQ
    private Mono<Void> handle(Message message) {
        return Mono.defer(() -> processor.apply(message))
                .then(Mono.defer(() -> delete(message)))
                .onErrorResume(error -> {
                    log.warn("Message {} failed and will be redelivered", message.messageId(), error);
                    return Mono.empty();
                });
    }

    private Mono<Void> delete(Message message) {
        return Mono.fromFuture(() -> client.deleteMessage(request -> request
                        .queueUrl(properties.queueUrl())
                        .receiptHandle(message.receiptHandle())))
                .then();
    }
}
