package co.com.nequi.ticketing.sqs.sender.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

import java.net.URI;
import java.time.Duration;

// The only SqsAsyncClient of the application; the listener entry point reuses it
@Configuration
@EnableConfigurationProperties(SQSSenderProperties.class)
public class SQSSenderConfig {

    // Long polling waits up to 20 s, so the call timeout must be longer than that
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(25);

    @Bean
    public SqsAsyncClient sqsAsyncClient(SQSSenderProperties properties, MetricPublisher publisher) {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(o -> o.addMetricPublisher(publisher).apiCallTimeout(API_CALL_TIMEOUT))
                .credentialsProvider(credentials(properties));
        if (properties.endpoint() != null) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    // Opens after half of the last 20 publishes fail, so purchases fail fast while the queue is down
    @Bean
    public CircuitBreaker sqsSenderCircuitBreaker() {
        return CircuitBreaker.of("sqs-sender", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build());
    }

    // An explicit endpoint means LocalStack, which accepts any credentials; AWS uses the default chain (IAM role)
    private static AwsCredentialsProvider credentials(SQSSenderProperties properties) {
        return properties.endpoint() != null
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))
                : DefaultCredentialsProvider.builder().build();
    }
}
