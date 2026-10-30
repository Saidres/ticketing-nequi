package co.com.nequi.ticketing.sqs.sender;

import co.com.nequi.ticketing.sqs.sender.config.SQSSenderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSSenderTest {

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/ticketing-orders";

    @Mock
    private SqsAsyncClient client;

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendTheOrderIdAsTheMessageBody() {
        when(client.sendMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("M1").build()));

        StepVerifier.create(sender().publish("O1")).verifyComplete();

        ArgumentCaptor<Consumer<SendMessageRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(client).sendMessage(captor.capture());
        SendMessageRequest.Builder request = SendMessageRequest.builder();
        captor.getValue().accept(request);
        assertEquals(QUEUE_URL, request.build().queueUrl());
        assertEquals("O1", request.build().messageBody());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPropagateSqsFailures() {
        when(client.sendMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.failedFuture(SqsException.builder().message("down").build()));

        StepVerifier.create(sender().publish("O1")).expectError(SqsException.class).verify();
    }

    private SQSSender sender() {
        return new SQSSender(client, new SQSSenderProperties("us-east-1", QUEUE_URL, null));
    }
}
