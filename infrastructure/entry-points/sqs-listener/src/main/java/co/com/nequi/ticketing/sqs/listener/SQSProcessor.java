package co.com.nequi.ticketing.sqs.listener;

import co.com.nequi.ticketing.usecase.processorder.ProcessOrderUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.function.Function;

@Service
public class SQSProcessor implements Function<Message, Mono<Void>> {

    private final ProcessOrderUseCase processOrderUseCase;

    public SQSProcessor(ProcessOrderUseCase processOrderUseCase) {
        this.processOrderUseCase = processOrderUseCase;
    }

    @Override
    public Mono<Void> apply(Message message) {
        return processOrderUseCase.execute(message.body());
    }
}
