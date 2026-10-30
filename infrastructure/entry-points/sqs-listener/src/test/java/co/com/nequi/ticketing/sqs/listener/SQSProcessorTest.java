package co.com.nequi.ticketing.sqs.listener;

import co.com.nequi.ticketing.usecase.processorder.ProcessOrderUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSProcessorTest {

    @Mock
    private ProcessOrderUseCase processOrderUseCase;

    @InjectMocks
    private SQSProcessor processor;

    @Test
    void shouldProcessTheOrderWhoseIdIsTheMessageBody() {
        when(processOrderUseCase.execute("O1")).thenReturn(Mono.empty());

        StepVerifier.create(processor.apply(Message.builder().body("O1").build())).verifyComplete();
    }

    @Test
    void shouldPropagateFailuresSoTheMessageIsRedelivered() {
        when(processOrderUseCase.execute("O1")).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(processor.apply(Message.builder().body("O1").build()))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
