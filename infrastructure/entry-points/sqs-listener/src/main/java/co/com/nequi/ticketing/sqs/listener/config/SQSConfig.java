package co.com.nequi.ticketing.sqs.listener.config;

import co.com.nequi.ticketing.sqs.listener.helper.SQSListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.function.Function;

@Configuration
@EnableConfigurationProperties(SQSProperties.class)
public class SQSConfig {

    @Bean
    public SQSListener sqsListener(SqsAsyncClient client, SQSProperties properties,
                                   Function<Message, Mono<Void>> processor) {
        return new SQSListener(client, properties, processor);
    }
}
