package co.com.nequi.ticketing.sqs.listener.config;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class SQSConfigTest {

    @Test
    void shouldCreateAListenerThatWaitsForTheContextToStart() {
        SQSProperties properties = new SQSProperties("http://localhost:4566/000000000000/q", 20, 30, 10, 2);

        var listener = new SQSConfig().sqsListener(mock(SqsAsyncClient.class), properties, message -> Mono.empty());

        assertFalse(listener.isRunning());
    }
}
