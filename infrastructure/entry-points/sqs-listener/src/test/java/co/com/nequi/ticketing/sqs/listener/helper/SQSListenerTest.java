package co.com.nequi.ticketing.sqs.listener.helper;

import co.com.nequi.ticketing.sqs.listener.config.SQSProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSListenerTest {

    private static final SQSProperties PROPERTIES =
            new SQSProperties("http://localhost:4566/000000000000/ticketing-orders", 1, 30, 10, 1);
    private static final Message PROCESSED = Message.builder().messageId("M1").body("O1").receiptHandle("R1").build();
    private static final Message FAILING = Message.builder().messageId("M2").body("O2").receiptHandle("R2").build();

    @Mock
    private SqsAsyncClient client;

    private SQSListener listener;

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeleteOnlyTheMessagesThatWereProcessed() {
        givenMessages(PROCESSED, FAILING);
        when(client.deleteMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));
        listener = listenerWith(message -> "O1".equals(message.body())
                ? Mono.empty()
                : Mono.error(new IllegalStateException("processing failed")));

        StepVerifier.create(listener.poll()).verifyComplete();

        ArgumentCaptor<Consumer<DeleteMessageRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(client).deleteMessage(captor.capture());
        DeleteMessageRequest.Builder request = DeleteMessageRequest.builder();
        captor.getValue().accept(request);
        assertEquals("R1", request.build().receiptHandle());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSurviveAProcessorThatThrowsInsteadOfReturningAnError() {
        givenMessages(PROCESSED);
        listener = listenerWith(message -> {
            throw new IllegalStateException("bug in the processor");
        });

        StepVerifier.create(listener.poll()).verifyComplete();

        verify(client, never()).deleteMessage(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSurviveAFailedDelete() {
        givenMessages(PROCESSED);
        when(client.deleteMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.failedFuture(SqsException.builder().message("down").build()));
        listener = listenerWith(message -> Mono.empty());

        StepVerifier.create(listener.poll()).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSurviveAnUnreachableQueue() {
        when(client.receiveMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.failedFuture(SqsException.builder().message("down").build()));
        listener = listenerWith(message -> Mono.empty());

        StepVerifier.withVirtualTime(listener::poll)
                .thenAwait(Duration.ofSeconds(1))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepPollingWhileRunning() {
        when(client.receiveMessage(any(Consumer.class))).thenReturn(
                CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build()));
        listener = listenerWith(message -> Mono.empty());

        listener.start();

        assertTrue(listener.isRunning());
        verify(client, timeout(1000).atLeast(2)).receiveMessage(any(Consumer.class));
        listener.stop();
        assertFalse(listener.isRunning());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLongPollTheConfiguredQueue() {
        givenMessages();
        listener = listenerWith(message -> Mono.empty());

        StepVerifier.create(listener.poll()).verifyComplete();

        ArgumentCaptor<Consumer<ReceiveMessageRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(client).receiveMessage(captor.capture());
        ReceiveMessageRequest.Builder request = ReceiveMessageRequest.builder();
        captor.getValue().accept(request);
        ReceiveMessageRequest built = request.build();
        assertEquals(PROPERTIES.queueUrl(), built.queueUrl());
        assertEquals(1, built.waitTimeSeconds());
        assertEquals(30, built.visibilityTimeout());
        assertEquals(10, built.maxNumberOfMessages());
    }

    @SuppressWarnings("unchecked")
    private void givenMessages(Message... messages) {
        when(client.receiveMessage(any(Consumer.class))).thenReturn(CompletableFuture.completedFuture(
                ReceiveMessageResponse.builder().messages(messages).build()));
    }

    private SQSListener listenerWith(Function<Message, Mono<Void>> processor) {
        return new SQSListener(client, PROPERTIES, processor);
    }
}
