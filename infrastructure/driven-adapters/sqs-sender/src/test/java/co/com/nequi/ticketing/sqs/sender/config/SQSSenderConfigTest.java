package co.com.nequi.ticketing.sqs.sender.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.metrics.LoggingMetricPublisher;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQSSenderConfigTest {

    private final SQSSenderConfig config = new SQSSenderConfig();

    @Test
    void shouldPointToLocalStackWhenAnEndpointIsConfigured() {
        SQSSenderProperties properties =
                new SQSSenderProperties("us-east-1", "http://localhost:4566/000000000000/q", "http://localhost:4566");

        try (SqsAsyncClient client = config.sqsAsyncClient(properties, LoggingMetricPublisher.create())) {
            assertEquals(URI.create("http://localhost:4566"), client.serviceClientConfiguration().endpointOverride().orElseThrow());
        }
    }

    @Test
    void shouldUseTheRegionalAwsEndpointOtherwise() {
        SQSSenderProperties properties = new SQSSenderProperties("us-east-1", "https://sqs.us-east-1.amazonaws.com/1/q", null);

        try (SqsAsyncClient client = config.sqsAsyncClient(properties, LoggingMetricPublisher.create())) {
            assertTrue(client.serviceClientConfiguration().endpointOverride().isEmpty());
        }
    }
}
